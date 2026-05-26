package com.secvulmanager.api.service;

import com.secvulmanager.api.model.*;
import com.secvulmanager.api.repository.CustomerRepository;
import com.secvulmanager.api.repository.CustomerSoftwareAccessRepository;
import com.secvulmanager.api.repository.CustomerTemplateRepository;
import com.secvulmanager.api.repository.UploadDetailsRepository;
import com.secvulmanager.api.repository.VulnerabilityFindingRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ETLService {

    private final CustomerRepository customerRepository;
    private final CustomerTemplateRepository templateRepository;
    private final CustomerSoftwareAccessRepository customerSoftwareAccessRepository;
    private final UploadDetailsRepository uploadRepository;
    private final VulnerabilityFindingRepository findingRepository;
    private final MappingEngineService mappingEngineService;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService ingestionExecutor = Executors.newFixedThreadPool(2);

    @Value("${secvulmanager.failed-uploads-dir:./failed-uploads}")
    private String failedUploadsDir;

    @Value("${secvulmanager.scan-uploads-dir:./scan-uploads}")
    private String scanUploadsDir;

    @Value("${secvulmanager.ingestion.batch-size:500}")
    private int ingestionBatchSize;

    @Value("${secvulmanager.ingestion.max-rows:200000}")
    private int maxRows;

    @Value("${secvulmanager.ingestion.max-columns:300}")
    private int maxColumns;

    @Value("${secvulmanager.ingestion.max-xls-size-bytes:15728640}")
    private long maxXlsSizeBytes;

    @Value("${secvulmanager.ingestion.max-xlsx-size-bytes:52428800}")
    private long maxXlsxSizeBytes;

    public ETLService(CustomerRepository customerRepository,
                      CustomerTemplateRepository templateRepository,
                      CustomerSoftwareAccessRepository customerSoftwareAccessRepository,
                      UploadDetailsRepository uploadRepository,
                      VulnerabilityFindingRepository findingRepository,
                      MappingEngineService mappingEngineService,
                      TransactionTemplate transactionTemplate) {
        this.customerRepository = customerRepository;
        this.templateRepository = templateRepository;
        this.customerSoftwareAccessRepository = customerSoftwareAccessRepository;
        this.uploadRepository = uploadRepository;
        this.findingRepository = findingRepository;
        this.mappingEngineService = mappingEngineService;
        this.transactionTemplate = transactionTemplate;
    }

    @PreDestroy
    public void shutdownExecutor() {
        ingestionExecutor.shutdown();
    }

    public IngestionSubmission submitUpload(MultipartFile file,
                                            UUID customerId,
                                            UUID templateId,
                                            String operatorUsername,
                                            Enums.QueueMode queueMode,
                                            String queueComment) throws Exception {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        CustomerTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Template layout not found"));
        validateUploadScope(customer, template, customerId);
        validateUploadSize(file, template);

        Enums.QueueMode effectiveQueueMode = queueMode != null ? queueMode : Enums.QueueMode.REJECT_IF_BUSY;
        UploadDetails laneOccupant = uploadRepository.findRunningForCustomer(customerId).stream()
                .findFirst()
                .orElseGet(() -> uploadRepository.findQueuedForCustomer(customerId).stream().findFirst().orElse(null));
        if (laneOccupant != null && effectiveQueueMode == Enums.QueueMode.REJECT_IF_BUSY) {
            return IngestionSubmission.busy(laneOccupant);
        }

        UploadDetails runLog = new UploadDetails(customer, template, operatorUsername, file.getOriginalFilename());
        runLog.setStatus(Enums.UploadStatus.PROCESSING);
        runLog.setQueueMode(effectiveQueueMode);
        runLog.setQueueComment(hasText(queueComment) ? queueComment.trim() : null);
        runLog.setReplaceActiveWhenDone(effectiveQueueMode == Enums.QueueMode.FORCE_ACTIVATE_WHEN_DONE);
        if (laneOccupant != null) {
            runLog.setProcessingStage(Enums.ProcessingStage.QUEUED);
            runLog.setQueuedAt(OffsetDateTime.now());
        } else {
            runLog.setProcessingStage(Enums.ProcessingStage.FILE_STORED);
            runLog.setStartedAt(OffsetDateTime.now());
        }

        try {
            runLog = uploadRepository.save(runLog);
        } catch (DataIntegrityViolationException e) {
            UploadDetails current = uploadRepository.findRunningForCustomer(customerId).stream().findFirst().orElse(null);
            if (current != null && effectiveQueueMode == Enums.QueueMode.REJECT_IF_BUSY) {
                return IngestionSubmission.busy(current);
            }
            if (current != null && effectiveQueueMode != Enums.QueueMode.REJECT_IF_BUSY) {
                runLog.setProcessingStage(Enums.ProcessingStage.QUEUED);
                runLog.setQueuedAt(OffsetDateTime.now());
                runLog.setStartedAt(null);
                runLog = uploadRepository.save(runLog);
            } else {
                throw e;
            }
        }

        try {
            Path uploadedScanPath = safeScanUploadPath(runLog, file);
            Files.copy(file.getInputStream(), uploadedScanPath, StandardCopyOption.REPLACE_EXISTING);
            runLog.setSampleFilePath(uploadedScanPath.toAbsolutePath().toString());
            runLog.setUploadedFilePath(uploadedScanPath.toAbsolutePath().toString());
            runLog = uploadRepository.save(runLog);
        } catch (Exception e) {
            runLog.setStatus(Enums.UploadStatus.FAILED);
            runLog.setProcessingStage(Enums.ProcessingStage.FAILED);
            runLog.setFinishedAt(OffsetDateTime.now());
            runLog.setErrorSummary("Failed to store uploaded scan file: " + e.getMessage());
            return IngestionSubmission.accepted(uploadRepository.save(runLog));
        }

        if (runLog.getProcessingStage() == Enums.ProcessingStage.QUEUED) {
            return IngestionSubmission.accepted(runLog);
        }

        dispatchUpload(runLog.getId());
        return IngestionSubmission.accepted(runLog);
    }

    public void dispatchQueuedUploads() {
        for (UploadDetails queued : uploadRepository.findQueuedUploads()) {
            try {
                if (uploadRepository.claimQueuedUpload(queued.getId()) == 1) {
                    dispatchUpload(queued.getId());
                }
            } catch (DataIntegrityViolationException ignored) {
                // Another request claimed this customer's processing slot first.
            }
        }
    }

    private void dispatchUpload(UUID uploadId) {
        ingestionExecutor.submit(() -> {
            try {
                processStoredUpload(uploadId);
            } catch (Exception e) {
                uploadRepository.findById(uploadId).ifPresent(run -> markFailed(run, "Upload processing failed unexpectedly: " + e.getMessage()));
            } finally {
                dispatchQueuedUploads();
            }
        });
    }

    private UUID processStoredUpload(UUID uploadId) {
        UploadDetails runLog = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("Upload not found"));
        UUID customerId = runLog.getCustomer().getId();
        Customer customer = runLog.getCustomer();
        CustomerTemplate template = runLog.getTemplate();

        try {
            validateUploadScope(customer, template, customerId);
        } catch (Exception e) {
            markFailed(runLog, e.getMessage());
            return customerId;
        }

        updateStage(runLog, Enums.ProcessingStage.VALIDATING_TEMPLATE);
        MappingEngineService.MappingConfiguration mappingConfiguration = mappingEngineService.loadMappingConfiguration(template);
        if (!mappingConfiguration.ready()) {
            markFailed(runLog, mappingConfiguration.errorMessage());
            return customerId;
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

        updateStage(runLog, Enums.ProcessingStage.READING_FILE);
        try (InputStream is = Files.newInputStream(Paths.get(runLog.getUploadedFilePath()))) {
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
                        validateColumnCount(headers.length);
                    }
                    updateStage(runLog, Enums.ProcessingStage.VALIDATING_HEADERS);
                    MappingEngineService.TemplateCompatibilityResult compatibility = mappingEngineService.validateTemplateCompatibility(mappings, headers, hasHeaderRow);
                    if (!compatibility.valid()) {
                        markFailed(runLog, compatibility.message());
                        return customerId;
                    }
                    MappingEngineService.MappingPlan mappingPlan = mappingEngineService.compile(mappings, headers, hasHeaderRow);
                    String[] errorFileHeaders = headers != null ? mappingEngineService.buildErrorHeaders(headers) : null;

                    updateStage(runLog, Enums.ProcessingStage.PROCESSING_ROWS);
                    while (iterator.hasNext()) {
                        CSVRecord record = iterator.next();
                        String[] row = csvRecordToArray(record);
                        validateRowLimits(totalRecords + 1, row.length);
                        if (!hasHeaderRow && totalRecords == 0 && compatibility.maxRequiredSourceColumnIndex() >= row.length) {
                            markFailed(runLog, "Uploaded file does not have enough columns for this active template. Expected source column index "
                                    + compatibility.maxRequiredSourceColumnIndex()
                                    + " but first data row has "
                                    + row.length
                                    + " column"
                                    + (row.length == 1 ? "" : "s")
                                    + ".");
                            return customerId;
                        }
                        totalRecords++;
                        runLog.setProcessedRecords(totalRecords);
                        if (errorFileHeaders == null) {
                            errorFileHeaders = mappingEngineService.buildGeneratedErrorHeaders(row.length, mappingPlan);
                        }

                        MappingEngineService.RowProcessingResult result = mappingEngineService.processDataRow(row, mappingPlan, runLog, customer);
                        if (result.valid()) {
                            validBatch.add(result.finding());
                            successfulRecords++;
                            if (validBatch.size() >= effectiveBatchSize()) {
                                updateStage(runLog, Enums.ProcessingStage.SAVING_FINDINGS);
                                persistFindingBatch(validBatch);
                                runLog = uploadRepository.findById(uploadId).orElse(runLog);
                                updateStage(runLog, Enums.ProcessingStage.PROCESSING_ROWS);
                            }
                        } else {
                            failedRecords++;
                            if (firstFailedReasons.size() < 3) {
                                firstFailedReasons.add(result.errorReason());
                            }
                            if (failedRowWriter == null) {
                                updateStage(runLog, Enums.ProcessingStage.WRITING_FAILED_ROWS);
                                failedRowWriter = FailedRowWriter.open(format, failedUploadsDir, runLog.getId(), errorFileHeaders);
                                updateStage(runLog, Enums.ProcessingStage.PROCESSING_ROWS);
                            }
                            failedRowWriter.write(row, result.errorReason());
                        }
                        if (totalRecords % effectiveBatchSize() == 0) {
                            uploadRepository.save(runLog);
                        }
                    }
                }
            } else if (format.equals("XLS") || format.equals("XLSX")) {
                validateSpreadsheetFileSize(Paths.get(runLog.getUploadedFilePath()), format);
                try (Workbook workbook = WorkbookFactory.create(is)) {
                    Sheet sheet = workbook.getSheetAt(0);
                    int startIdx = 0;
                    String[] headers = null;
                    if (hasHeaderRow) {
                        Row headerRow = sheet.getRow(0);
                        if (headerRow != null) {
                            headers = new String[headerRow.getLastCellNum()];
                            validateColumnCount(headers.length);
                            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                                Cell cell = headerRow.getCell(i);
                                headers[i] = getCellValueAsString(cell);
                            }
                            startIdx = 1;
                        }
                    }
                    updateStage(runLog, Enums.ProcessingStage.VALIDATING_HEADERS);
                    MappingEngineService.TemplateCompatibilityResult compatibility = mappingEngineService.validateTemplateCompatibility(mappings, headers, hasHeaderRow);
                    if (!compatibility.valid()) {
                        markFailed(runLog, compatibility.message());
                        return customerId;
                    }
                    MappingEngineService.MappingPlan mappingPlan = mappingEngineService.compile(mappings, headers, hasHeaderRow);
                    String[] errorFileHeaders = headers != null ? mappingEngineService.buildErrorHeaders(headers) : null;
                    updateStage(runLog, Enums.ProcessingStage.PROCESSING_ROWS);
                    for (int i = startIdx; i <= sheet.getLastRowNum(); i++) {
                        Row r = sheet.getRow(i);
                        if (r == null) continue;
                        String[] row = new String[r.getLastCellNum() > 0 ? r.getLastCellNum() : 0];
                        validateRowLimits(totalRecords + 1, row.length);
                        for (int j = 0; j < r.getLastCellNum(); j++) {
                            Cell cell = r.getCell(j);
                            row[j] = getCellValueAsString(cell);
                        }
                        if (!hasHeaderRow && totalRecords == 0 && compatibility.maxRequiredSourceColumnIndex() >= row.length) {
                            markFailed(runLog, "Uploaded file does not have enough columns for this active template. Expected source column index "
                                    + compatibility.maxRequiredSourceColumnIndex()
                                    + " but first data row has "
                                    + row.length
                                    + " column"
                                    + (row.length == 1 ? "" : "s")
                                    + ".");
                            return customerId;
                        }
                        totalRecords++;
                        runLog.setProcessedRecords(totalRecords);
                        if (errorFileHeaders == null) {
                            errorFileHeaders = mappingEngineService.buildGeneratedErrorHeaders(row.length, mappingPlan);
                        }

                        MappingEngineService.RowProcessingResult result = mappingEngineService.processDataRow(row, mappingPlan, runLog, customer);
                        if (result.valid()) {
                            validBatch.add(result.finding());
                            successfulRecords++;
                            if (validBatch.size() >= effectiveBatchSize()) {
                                updateStage(runLog, Enums.ProcessingStage.SAVING_FINDINGS);
                                persistFindingBatch(validBatch);
                                runLog = uploadRepository.findById(uploadId).orElse(runLog);
                                updateStage(runLog, Enums.ProcessingStage.PROCESSING_ROWS);
                            }
                        } else {
                            failedRecords++;
                            if (firstFailedReasons.size() < 3) {
                                firstFailedReasons.add(result.errorReason());
                            }
                            if (failedRowWriter == null) {
                                updateStage(runLog, Enums.ProcessingStage.WRITING_FAILED_ROWS);
                                failedRowWriter = FailedRowWriter.open(format, failedUploadsDir, runLog.getId(), errorFileHeaders);
                                updateStage(runLog, Enums.ProcessingStage.PROCESSING_ROWS);
                            }
                            failedRowWriter.write(row, result.errorReason());
                        }
                        if (totalRecords % effectiveBatchSize() == 0) {
                            uploadRepository.save(runLog);
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (totalRecords > 0) {
                findingRepository.deleteByUploadIdBulk(runLog.getId());
                findingRepository.flush();
                runLog = uploadRepository.findById(runLog.getId()).orElse(runLog);
            }
            markFailedWithCounts(runLog, totalRecords, "Failed to process or save uploaded rows: " + e.getMessage());
            return customerId;
        } finally {
            if (failedRowWriter != null) {
                try {
                    failedRowWriter.close();
                } catch (IOException e) {
                    runLog.setErrorSummary(appendSummary(runLog.getErrorSummary(), "Failed-row log could not be finalized: " + e.getMessage()));
                    uploadRepository.save(runLog);
                }
            }
        }

        if (totalRecords == 0) {
            markFailed(runLog, "Uploaded file contains zero data rows");
            return customerId;
        }

        if (!validBatch.isEmpty()) {
            try {
                updateStage(runLog, Enums.ProcessingStage.SAVING_FINDINGS);
                persistFindingBatch(validBatch);
                runLog = uploadRepository.findById(runLog.getId()).orElse(runLog);
            } catch (Exception e) {
                findingRepository.deleteByUploadIdBulk(runLog.getId());
                findingRepository.flush();
                runLog = uploadRepository.findById(runLog.getId()).orElse(runLog);
                markFailedWithCounts(runLog, totalRecords, "Failed to save final finding batch: " + e.getMessage());
                return customerId;
            }
        }

        runLog.setTotalRecords(totalRecords);
        runLog.setFailedRecords(failedRecords);
        runLog.setSuccessfulRecords(successfulRecords);
        runLog.setWarningRecords(0);

        boolean shouldActivate = false;
        if (successfulRecords > 0) {
            boolean majoritySucceeded = successfulRecords * 2 >= totalRecords;
            shouldActivate = majoritySucceeded || runLog.isReplaceActiveWhenDone();
            if (shouldActivate) {
                updateStage(runLog, Enums.ProcessingStage.ACTIVATING_SNAPSHOT);
            }
            if (failedRecords == 0) {
                runLog.setStatus(Enums.UploadStatus.SUCCESS);
            } else {
                runLog.setStatus(Enums.UploadStatus.PARTIAL_FAILURE);
                if (shouldActivate) {
                    runLog.setErrorSummary("Partially ingested " + successfulRecords + " records. " + failedRecords + " rows failed constraints. This upload replaced the previous active snapshot.");
                } else {
                    runLog.setErrorSummary("Partially ingested " + successfulRecords + " records. " + failedRecords + " rows failed constraints. This upload was kept historical because fewer than 50% of rows succeeded. Review the upload and activate it manually if it should replace the current active snapshot.");
                }
            }
        } else {
            runLog.setStatus(Enums.UploadStatus.FAILED);
            runLog.setErrorSummary("All rows in the uploaded file failed validations: " + String.join("; ", firstFailedReasons));
        }

        if (failedRowWriter != null) {
            runLog.setErrorLogPath(failedRowWriter.file().getAbsolutePath());
        }

        runLog.setProcessingStage(runLog.getStatus() == Enums.UploadStatus.FAILED ? Enums.ProcessingStage.FAILED : Enums.ProcessingStage.COMPLETED);
        runLog.setFinishedAt(OffsetDateTime.now());
        finalizeProcessedUpload(runLog, customerId, template.getSoftware().getId(), shouldActivate);
        return customerId;
    }

    public UploadDetails activateUploadSnapshot(UUID uploadId) {
        return transactionTemplate.execute(status -> {
            UploadDetails runLog = uploadRepository.findById(uploadId)
                    .orElseThrow(() -> new IllegalArgumentException("Upload not found"));
            if (runLog.getSoftware() == null) {
                throw new IllegalArgumentException("Upload has no software reference and cannot be activated");
            }
            if (runLog.getSuccessfulRecords() <= 0 || runLog.getStatus() == Enums.UploadStatus.FAILED) {
                throw new IllegalArgumentException("Only successful or partially successful uploads can be activated");
            }
            uploadRepository.clearActiveSnapshotsForSoftwareExcept(runLog.getCustomer().getId(), runLog.getSoftware().getId(), runLog.getId());
            runLog.setActiveSnapshot(true);
            return uploadRepository.save(runLog);
        });
    }

    private UploadDetails finalizeProcessedUpload(UploadDetails runLog, UUID customerId, UUID softwareId, boolean shouldActivate) {
        return transactionTemplate.execute(status -> {
            UploadDetails persisted = uploadRepository.findById(runLog.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Upload not found"));
            persisted.setTotalRecords(runLog.getTotalRecords());
            persisted.setProcessedRecords(runLog.getProcessedRecords());
            persisted.setFailedRecords(runLog.getFailedRecords());
            persisted.setSuccessfulRecords(runLog.getSuccessfulRecords());
            persisted.setWarningRecords(runLog.getWarningRecords());
            persisted.setStatus(runLog.getStatus());
            persisted.setErrorSummary(runLog.getErrorSummary());
            persisted.setErrorLogPath(runLog.getErrorLogPath());
            persisted.setProcessingStage(runLog.getProcessingStage());
            persisted.setFinishedAt(runLog.getFinishedAt());
            if (shouldActivate && softwareId != null && persisted.getStatus() != Enums.UploadStatus.FAILED) {
                uploadRepository.clearActiveSnapshotsForSoftwareExcept(customerId, softwareId, persisted.getId());
                persisted.setActiveSnapshot(true);
            }
            return uploadRepository.save(persisted);
        });
    }

    private void validateUploadScope(Customer customer, CustomerTemplate template, UUID customerId) {
        if (!template.isEnabled()) {
            throw new IllegalArgumentException("Template is disabled. Enable and save the template before scan upload ingestion.");
        }
        if (customer.isArchived() || !customer.isEnabled()) {
            throw new IllegalArgumentException("Customer is not active and enabled for scan upload ingestion.");
        }
        if (template.isArchived() || template.getSoftware() == null || template.getSoftware().isArchived() || !template.getSoftware().isEnabled()) {
            throw new IllegalArgumentException("Template software is not active and enabled for this upload.");
        }
        if (template.getCustomer() != null && !template.getCustomer().getId().equals(customerId)) {
            throw new IllegalArgumentException("Template belongs to a different customer.");
        }
        if (!customerSoftwareAccessRepository.existsByCustomerIdAndSoftwareIdAndEnabledTrue(customerId, template.getSoftware().getId())) {
            throw new IllegalArgumentException("This customer does not have the template software assigned and enabled.");
        }
    }

    private void validateUploadSize(MultipartFile file, CustomerTemplate template) {
        String format = template.getFileFormat() != null ? template.getFileFormat().name() : "CSV";
        if ("XLS".equals(format) && file.getSize() > maxXlsSizeBytes) {
            throw new IllegalArgumentException("XLS uploads are limited to " + maxXlsSizeBytes + " bytes to avoid excessive memory use.");
        }
        if ("XLSX".equals(format) && file.getSize() > maxXlsxSizeBytes) {
            throw new IllegalArgumentException("XLSX uploads are limited to " + maxXlsxSizeBytes + " bytes to avoid excessive memory use.");
        }
    }

    private void validateSpreadsheetFileSize(Path path, String format) throws IOException {
        long size = Files.size(path);
        if ("XLS".equals(format) && size > maxXlsSizeBytes) {
            throw new IOException("XLS upload exceeds configured size limit.");
        }
        if ("XLSX".equals(format) && size > maxXlsxSizeBytes) {
            throw new IOException("XLSX upload exceeds configured size limit.");
        }
    }

    private void validateRowLimits(int rowNumber, int columnCount) {
        if (rowNumber > maxRows) {
            throw new IllegalArgumentException("Uploaded file exceeds the configured maximum row limit of " + maxRows + ".");
        }
        validateColumnCount(columnCount);
    }

    private void validateColumnCount(int columnCount) {
        if (columnCount > maxColumns) {
            throw new IllegalArgumentException("Uploaded file exceeds the configured maximum column limit of " + maxColumns + ".");
        }
    }

    private void updateStage(UploadDetails runLog, Enums.ProcessingStage stage) {
        runLog.setProcessingStage(stage);
        uploadRepository.save(runLog);
    }

    private void markFailed(UploadDetails runLog, String message) {
        runLog.setStatus(Enums.UploadStatus.FAILED);
        runLog.setProcessingStage(Enums.ProcessingStage.FAILED);
        runLog.setFinishedAt(OffsetDateTime.now());
        runLog.setErrorSummary(message);
        uploadRepository.save(runLog);
    }

    private void markFailedWithCounts(UploadDetails runLog, int processedRecords, String message) {
        runLog.setTotalRecords(processedRecords);
        runLog.setProcessedRecords(processedRecords);
        runLog.setSuccessfulRecords(0);
        runLog.setFailedRecords(processedRecords);
        runLog.setWarningRecords(0);
        markFailed(runLog, message);
    }

    private String appendSummary(String existing, String addition) {
        if (!hasText(existing)) {
            return addition;
        }
        return existing + " " + addition;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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

    public record IngestionSubmission(boolean busy, UploadDetails upload, UploadDetails runningUpload) {
        static IngestionSubmission busy(UploadDetails runningUpload) {
            return new IngestionSubmission(true, null, runningUpload);
        }

        static IngestionSubmission accepted(UploadDetails upload) {
            return new IngestionSubmission(false, upload, null);
        }
    }

    private void persistFindingBatch(List<VulnerabilityFinding> validBatch) {
        if (validBatch.isEmpty()) {
            return;
        }
        findingRepository.saveAll(validBatch);
        findingRepository.flush();
        validBatch.clear();
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
