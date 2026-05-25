package com.secvulmanager.api.service;

import com.secvulmanager.api.model.*;
import com.secvulmanager.api.repository.CustomerRepository;
import com.secvulmanager.api.repository.CustomerSoftwareAccessRepository;
import com.secvulmanager.api.repository.CustomerTemplateRepository;
import com.secvulmanager.api.repository.UploadDetailsRepository;
import com.secvulmanager.api.repository.VulnerabilityFindingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Service
public class ETLService {

    private final CustomerRepository customerRepository;
    private final CustomerTemplateRepository templateRepository;
    private final CustomerSoftwareAccessRepository customerSoftwareAccessRepository;
    private final UploadDetailsRepository uploadRepository;
    private final VulnerabilityFindingRepository findingRepository;
    private final MappingEngineService mappingEngineService;

    @Value("${secvulmanager.failed-uploads-dir:./failed-uploads}")
    private String failedUploadsDir;

    @Value("${secvulmanager.scan-uploads-dir:./scan-uploads}")
    private String scanUploadsDir;

    @Value("${secvulmanager.ingestion.batch-size:500}")
    private int ingestionBatchSize;

    @PersistenceContext
    private EntityManager entityManager;

    public ETLService(CustomerRepository customerRepository,
                      CustomerTemplateRepository templateRepository,
                      CustomerSoftwareAccessRepository customerSoftwareAccessRepository,
                      UploadDetailsRepository uploadRepository,
                      VulnerabilityFindingRepository findingRepository,
                      MappingEngineService mappingEngineService) {
        this.customerRepository = customerRepository;
        this.templateRepository = templateRepository;
        this.customerSoftwareAccessRepository = customerSoftwareAccessRepository;
        this.uploadRepository = uploadRepository;
        this.findingRepository = findingRepository;
        this.mappingEngineService = mappingEngineService;
    }

    /**
     * Executes the comprehensive ingestion transaction.
     */
    @Transactional
    public UploadDetails ingestFile(MultipartFile file, UUID customerId, UUID templateId, String operatorUsername) throws Exception {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        CustomerTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template layout not found"));

        UploadDetails runLog = new UploadDetails(customer, template, operatorUsername, file.getOriginalFilename());
        runLog.setStatus(Enums.UploadStatus.PROCESSING);
        runLog = uploadRepository.save(runLog);

        if (!template.isEnabled()) {
            runLog.setStatus(Enums.UploadStatus.FAILED);
            runLog.setErrorSummary("Template is Disabled. Enable and save the template before scan upload ingestion.");
            return uploadRepository.save(runLog);
        }
        if (customer.isArchived() || !customer.isEnabled()) {
            runLog.setStatus(Enums.UploadStatus.FAILED);
            runLog.setErrorSummary("Customer is not active and enabled for scan upload ingestion.");
            return uploadRepository.save(runLog);
        }
        if (template.isArchived() || template.getSoftware() == null || template.getSoftware().isArchived() || !template.getSoftware().isEnabled()) {
            runLog.setStatus(Enums.UploadStatus.FAILED);
            runLog.setErrorSummary("Template software is not active and enabled for this upload.");
            return uploadRepository.save(runLog);
        }
        if (template.getCustomer() != null && !template.getCustomer().getId().equals(customerId)) {
            runLog.setStatus(Enums.UploadStatus.FAILED);
            runLog.setErrorSummary("Template belongs to a different customer.");
            return uploadRepository.save(runLog);
        }
        if (!customerSoftwareAccessRepository.existsByCustomerIdAndSoftwareIdAndEnabledTrue(customerId, template.getSoftware().getId())) {
            runLog.setStatus(Enums.UploadStatus.FAILED);
            runLog.setErrorSummary("This customer does not have the template software assigned and enabled.");
            return uploadRepository.save(runLog);
        }

        try {
            Path uploadedScanPath = safeScanUploadPath(runLog, file);
            Files.copy(file.getInputStream(), uploadedScanPath, StandardCopyOption.REPLACE_EXISTING);
            runLog.setSampleFilePath(uploadedScanPath.toAbsolutePath().toString());
            runLog = uploadRepository.save(runLog);
        } catch (Exception e) {
            runLog.setStatus(Enums.UploadStatus.FAILED);
            runLog.setErrorSummary("Failed to store uploaded scan file: " + e.getMessage());
            return uploadRepository.save(runLog);
        }

        MappingEngineService.MappingConfiguration mappingConfiguration = mappingEngineService.loadMappingConfiguration(template);
        if (!mappingConfiguration.ready()) {
            runLog.setStatus(Enums.UploadStatus.FAILED);
            runLog.setErrorSummary(mappingConfiguration.errorMessage());
            return uploadRepository.save(runLog);
        }
        List<Map<String, Object>> mappings = mappingConfiguration.mappings();

        String format = template.getFileFormat() != null ? template.getFileFormat().name() : "CSV";
        boolean hasHeaderRow = template.isHasHeaderRow();

        int totalRecords = 0;
        int failedRecords = 0;
        int successfulRecords = 0;
        List<String> firstFailedReasons = new ArrayList<>();
        List<VulnerabilityFinding> validBatch = new ArrayList<>(Math.max(1, ingestionBatchSize));
        FailedRowWriter failedRowWriter = null;

        try (InputStream is = file.getInputStream()) {
            if (format.equals("CSV") || format.equals("TSV") || format.equals("PSV")) {
                CSVFormat csvFormat = CSVFormat.DEFAULT;
                if (format.equals("TSV")) csvFormat = csvFormat.withDelimiter('\t');
                else if (format.equals("PSV")) csvFormat = csvFormat.withDelimiter('|');
                
                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                     CSVParser parser = new CSVParser(reader, csvFormat)) {
                    Iterator<CSVRecord> iterator = parser.iterator();
                    String[] headers = null;
                    if (hasHeaderRow && iterator.hasNext()) {
                        CSVRecord headerRec = iterator.next();
                        headers = csvRecordToArray(headerRec);
                    }
                    MappingEngineService.MappingPlan mappingPlan = mappingEngineService.compile(mappings, headers, hasHeaderRow);
                    String[] errorFileHeaders = headers != null ? mappingEngineService.buildErrorHeaders(headers) : null;

                    while (iterator.hasNext()) {
                        CSVRecord record = iterator.next();
                        String[] row = csvRecordToArray(record);
                        totalRecords++;
                        if (errorFileHeaders == null) {
                            errorFileHeaders = mappingEngineService.buildGeneratedErrorHeaders(row.length, mappingPlan);
                        }

                        MappingEngineService.RowProcessingResult result = mappingEngineService.processDataRow(row, mappingPlan, runLog, customer);
                        if (result.valid()) {
                            validBatch.add(result.finding());
                            successfulRecords++;
                            if (validBatch.size() >= effectiveBatchSize()) {
                                persistFindingBatch(validBatch);
                                runLog = entityManager.getReference(UploadDetails.class, runLog.getId());
                                customer = entityManager.getReference(Customer.class, customer.getId());
                            }
                        } else {
                            failedRecords++;
                            if (firstFailedReasons.size() < 3) {
                                firstFailedReasons.add(result.errorReason());
                            }
                            if (failedRowWriter == null) {
                                failedRowWriter = FailedRowWriter.open(format, failedUploadsDir, runLog.getId(), errorFileHeaders);
                            }
                            failedRowWriter.write(row, result.errorReason());
                        }
                    }
                }
            } else if (format.equals("XLS") || format.equals("XLSX")) {
                try (Workbook workbook = WorkbookFactory.create(is)) {
                    Sheet sheet = workbook.getSheetAt(0);
                    int startIdx = 0;
                    String[] headers = null;
                    if (hasHeaderRow) {
                        Row headerRow = sheet.getRow(0);
                        if (headerRow != null) {
                            headers = new String[headerRow.getLastCellNum()];
                            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                                Cell cell = headerRow.getCell(i);
                                headers[i] = getCellValueAsString(cell);
                            }
                            startIdx = 1;
                        }
                    }
                    MappingEngineService.MappingPlan mappingPlan = mappingEngineService.compile(mappings, headers, hasHeaderRow);
                    String[] errorFileHeaders = headers != null ? mappingEngineService.buildErrorHeaders(headers) : null;
                    for (int i = startIdx; i <= sheet.getLastRowNum(); i++) {
                        Row r = sheet.getRow(i);
                        if (r == null) continue;
                        String[] row = new String[r.getLastCellNum() > 0 ? r.getLastCellNum() : 0];
                        for (int j = 0; j < r.getLastCellNum(); j++) {
                            Cell cell = r.getCell(j);
                            row[j] = getCellValueAsString(cell);
                        }
                        totalRecords++;
                        if (errorFileHeaders == null) {
                            errorFileHeaders = mappingEngineService.buildGeneratedErrorHeaders(row.length, mappingPlan);
                        }

                        MappingEngineService.RowProcessingResult result = mappingEngineService.processDataRow(row, mappingPlan, runLog, customer);
                        if (result.valid()) {
                            validBatch.add(result.finding());
                            successfulRecords++;
                            if (validBatch.size() >= effectiveBatchSize()) {
                                persistFindingBatch(validBatch);
                                runLog = entityManager.getReference(UploadDetails.class, runLog.getId());
                                customer = entityManager.getReference(Customer.class, customer.getId());
                            }
                        } else {
                            failedRecords++;
                            if (firstFailedReasons.size() < 3) {
                                firstFailedReasons.add(result.errorReason());
                            }
                            if (failedRowWriter == null) {
                                failedRowWriter = FailedRowWriter.open(format, failedUploadsDir, runLog.getId(), errorFileHeaders);
                            }
                            failedRowWriter.write(row, result.errorReason());
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (totalRecords > 0) {
                findingRepository.deleteByUploadIdBulk(runLog.getId());
                findingRepository.flush();
                entityManager.clear();
                runLog = uploadRepository.findById(runLog.getId()).orElse(runLog);
            }
            runLog.setStatus(Enums.UploadStatus.FAILED);
            runLog.setErrorSummary("Failed to parse file structural format: " + e.getMessage());
            return uploadRepository.save(runLog);
        } finally {
            if (failedRowWriter != null) {
                failedRowWriter.close();
            }
        }

        if (totalRecords == 0) {
            runLog.setStatus(Enums.UploadStatus.FAILED);
            runLog.setErrorSummary("Uploaded file contains zero data rows");
            return uploadRepository.save(runLog);
        }

        if (!validBatch.isEmpty()) {
            persistFindingBatch(validBatch);
            runLog = uploadRepository.findById(runLog.getId()).orElse(runLog);
        }

        runLog.setTotalRecords(totalRecords);
        runLog.setFailedRecords(failedRecords);

        if (successfulRecords > 0) {
            Optional<UploadDetails> activeSnapshot = uploadRepository.findActiveSnapshotForCustomer(customerId);
            if (activeSnapshot.isPresent()) {
                UploadDetails oldActive = activeSnapshot.get();
                oldActive.setActiveSnapshot(false);
                uploadRepository.save(oldActive);
            }

            runLog.setActiveSnapshot(true);
            if (failedRecords == 0) {
                runLog.setStatus(Enums.UploadStatus.SUCCESS);
            } else {
                runLog.setStatus(Enums.UploadStatus.PARTIAL_FAILURE);
                runLog.setErrorSummary("Partially ingested " + successfulRecords + " records. " + failedRecords + " rows failed constraints.");
            }
        } else {
            runLog.setStatus(Enums.UploadStatus.FAILED);
            runLog.setErrorSummary("All rows in the uploaded file failed validations: " + String.join("; ", firstFailedReasons));
        }

        if (failedRowWriter != null) {
            runLog.setErrorLogPath(failedRowWriter.file().getAbsolutePath());
        }

        return uploadRepository.save(runLog);
    }

    private Path safeScanUploadPath(UploadDetails runLog, MultipartFile file) throws IOException {
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        Path originalPath = Paths.get(originalName);
        String basename = originalPath.getFileName() != null ? originalPath.getFileName().toString() : "";
        String extension = "";
        int extensionIndex = basename.lastIndexOf('.');
        if (extensionIndex >= 0 && extensionIndex < basename.length() - 1) {
            extension = basename.substring(extensionIndex).replaceAll("[^A-Za-z0-9.]", "");
        }

        Path uploadRoot = Paths.get(scanUploadsDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot);
        String fileName = runLog.getCustomer().getId() + "_" + runLog.getId() + "_" + System.currentTimeMillis() + extension;
        Path targetLocation = uploadRoot.resolve(fileName).normalize();
        if (!targetLocation.startsWith(uploadRoot)) {
            throw new SecurityException("Invalid scan upload path");
        }
        return targetLocation;
    }

    private void persistFindingBatch(List<VulnerabilityFinding> validBatch) {
        if (validBatch.isEmpty()) {
            return;
        }
        findingRepository.saveAll(validBatch);
        findingRepository.flush();
        validBatch.clear();
        entityManager.clear();
    }

    private int effectiveBatchSize() {
        return Math.max(1, ingestionBatchSize);
    }

    private String[] csvRecordToArray(CSVRecord record) {
        String[] row = new String[record.size()];
        for (int i = 0; i < record.size(); i++) {
            row[i] = record.get(i);
        }
        return row;
    }

    private static class FailedRowWriter implements Closeable {
        private final File file;
        private final String[] headers;
        private final boolean spreadsheet;
        private CSVPrinter csvPrinter;
        private BufferedWriter csvWriter;
        private SXSSFWorkbook workbook;
        private Sheet sheet;
        private int rowIndex = 1;
        private boolean closed = false;

        private FailedRowWriter(File file, String[] headers, boolean spreadsheet) {
            this.file = file;
            this.headers = headers;
            this.spreadsheet = spreadsheet;
        }

        static FailedRowWriter open(String format, String failedUploadsDir, UUID runId, String[] headers) throws IOException {
            File dir = new File(failedUploadsDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IOException("Failed to create failed upload directory " + dir.getAbsolutePath());
            }
            boolean spreadsheetInput = format.equals("XLS") || format.equals("XLSX");
            File file = new File(dir, "error_" + runId + (spreadsheetInput ? ".xlsx" : ".csv"));
            FailedRowWriter writer = new FailedRowWriter(file, headers, spreadsheetInput);
            writer.open();
            return writer;
        }

        File file() {
            return file;
        }

        private void open() throws IOException {
            if (spreadsheet) {
                workbook = new SXSSFWorkbook(100);
                sheet = workbook.createSheet("Failed Rows");
                Row headerRow = sheet.createRow(0);
                for (int col = 0; col < headers.length; col++) {
                    headerRow.createCell(col).setCellValue(headers[col]);
                }
            } else {
                csvWriter = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8));
                csvPrinter = new CSVPrinter(csvWriter, CSVFormat.DEFAULT.withHeader(headers));
            }
        }

        void write(String[] failedRow, String failedReason) throws IOException {
            if (spreadsheet) {
                Row row = sheet.createRow(rowIndex++);
                for (int col = 0; col < headers.length - 1; col++) {
                    row.createCell(col).setCellValue(col < failedRow.length && failedRow[col] != null ? failedRow[col] : "");
                }
                row.createCell(headers.length - 1).setCellValue(failedReason);
            } else {
                String[] outputRow = Arrays.copyOf(failedRow, headers.length);
                outputRow[outputRow.length - 1] = failedReason;
                csvPrinter.printRecord((Object[]) outputRow);
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            if (spreadsheet) {
                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    workbook.write(outputStream);
                } finally {
                    workbook.dispose();
                    workbook.close();
                }
            } else {
                csvPrinter.flush();
                csvPrinter.close();
                csvWriter.close();
            }
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString() + "Z";
                }
                return BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
                }
            default:
                return "";
        }
    }
}
