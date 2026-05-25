package com.secvulmanager.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secvulmanager.api.model.*;
import com.secvulmanager.api.repository.AppUserRepository;
import com.secvulmanager.api.repository.CustomerRepository;
import com.secvulmanager.api.repository.CustomerSoftwareAccessRepository;
import com.secvulmanager.api.repository.CustomerTemplateRepository;
import com.secvulmanager.api.repository.SecuritySoftwareRepository;
import com.secvulmanager.api.repository.UserCustomerAccessRepository;
import com.secvulmanager.api.service.AuthorizationUtil;
import com.secvulmanager.api.service.DestinationSchemaService;
import com.secvulmanager.api.service.MappingEngineService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class CustomerTemplateController {

    private final CustomerTemplateRepository templateRepository;
    private final SecuritySoftwareRepository softwareRepository;
    private final CustomerRepository customerRepository;
    private final AppUserRepository userRepository;
    private final AuthorizationUtil authUtil;
    private final UserCustomerAccessRepository customerAccessRepository;
    private final CustomerSoftwareAccessRepository customerSoftwareAccessRepository;
    private final DestinationSchemaService destinationSchemaService;
    private final MappingEngineService mappingEngineService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Ensure this directory exists
    private final String UPLOAD_DIR = "uploads/templates/";

    public CustomerTemplateController(CustomerTemplateRepository templateRepository,
                                      SecuritySoftwareRepository softwareRepository,
                                      CustomerRepository customerRepository,
                                      AppUserRepository userRepository,
                                      UserCustomerAccessRepository customerAccessRepository,
                                      CustomerSoftwareAccessRepository customerSoftwareAccessRepository,
                                      AuthorizationUtil authUtil,
                                      DestinationSchemaService destinationSchemaService,
                                      MappingEngineService mappingEngineService) {
        this.templateRepository = templateRepository;
        this.softwareRepository = softwareRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.customerAccessRepository = customerAccessRepository;
        this.customerSoftwareAccessRepository = customerSoftwareAccessRepository;
        this.authUtil = authUtil;
        this.destinationSchemaService = destinationSchemaService;
        this.mappingEngineService = mappingEngineService;
        
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (Exception e) {
            throw new RuntimeException("Could not create template uploads directory!");
        }
    }

    @GetMapping("/templates/schema")
    public ResponseEntity<?> getTemplateSchema() {
        AppUser currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("fields", destinationSchemaService.fields()));
    }

    private AppUser getCurrentUser() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(name).orElse(null);
    }

    private boolean canManageTemplate(AppUser user, CustomerTemplate template) {
        if (template.getCustomer() == null) {
            return authUtil.isSuperAdmin(user) || authUtil.isSecurityOperator(user);
        }
        return authUtil.canManageTemplates(user, template.getCustomer().getId());
    }

    private Enums.FileFormat parseFormat(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Enums.FileFormat.CSV;
        }
        return Enums.FileFormat.valueOf(value.trim().toUpperCase());
    }

    private Path safeTemplateUploadPath(CustomerTemplate template, MultipartFile file) throws java.io.IOException {
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        String basename = Paths.get(originalName).getFileName() != null
                ? Paths.get(originalName).getFileName().toString()
                : "";
        String extension = "";
        int extensionIndex = basename.lastIndexOf('.');
        if (extensionIndex >= 0 && extensionIndex < basename.length() - 1) {
            extension = basename.substring(extensionIndex).replaceAll("[^A-Za-z0-9.]", "");
        }

        String ownerPrefix = template.getCustomer() != null ? template.getCustomer().getId().toString() : "global";
        String fileName = ownerPrefix + "_" + template.getId() + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID() + extension;
        Path uploadRoot = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
        Files.createDirectories(uploadRoot);
        Path targetLocation = uploadRoot.resolve(fileName).normalize();
        if (!targetLocation.startsWith(uploadRoot)) {
            throw new SecurityException("Invalid upload path");
        }
        return targetLocation;
    }

    @GetMapping("/templates")
    public ResponseEntity<?> getAllTemplates(@RequestParam(required = false) UUID softwareId,
                                             @RequestParam(required = false) UUID customerId) {
        AppUser currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (customerId != null) {
            if (!authUtil.canAccessCustomer(currentUser, customerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Access denied to customer\"}");
            }
            return ResponseEntity.ok(templateRepository.findByCustomerId(customerId));
        }

        if (softwareId != null) {
            return ResponseEntity.ok(templateRepository.findBySoftwareId(softwareId));
        }

        if (authUtil.isSuperAdminOrGlobalOperator(currentUser) || authUtil.isSecurityOperator(currentUser)) {
            return ResponseEntity.ok(templateRepository.findAll());
        }

        List<CustomerTemplate> templates = new ArrayList<>();
        customerAccessRepository.findByUserId(currentUser.getId())
                .forEach(access -> templates.addAll(templateRepository.findByCustomerId(access.getCustomer().getId())));
        templates.addAll(templateRepository.findByCustomerIsNull());
        return ResponseEntity.ok(templates);
    }

    // List all templates for a specific customer
    @GetMapping("/customers/{customerId}/templates")
    public ResponseEntity<?> getTemplatesForCustomer(@PathVariable UUID customerId) {
        AppUser currentUser = getCurrentUser();
        if (!authUtil.canAccessCustomer(currentUser, customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Access denied to customer\"}");
        }
        
        return ResponseEntity.ok(templateRepository.findByCustomerId(customerId));
    }

    // Create a new template for a customer and software
    @PostMapping("/customers/{customerId}/software/{softwareId}/templates")
    @Transactional
    public ResponseEntity<?> createTemplate(@PathVariable UUID customerId, 
                                            @PathVariable UUID softwareId,
                                            @RequestBody Map<String, Object> request) {
        AppUser currentUser = getCurrentUser();
        if (!authUtil.canManageTemplates(currentUser, customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Access denied to manage templates for customer\"}");
        }

        Customer cust = customerRepository.findById(customerId).orElse(null);
        SecuritySoftware sw = softwareRepository.findById(softwareId).orElse(null);

        if (cust == null || sw == null) {
            return ResponseEntity.badRequest().body("{\"error\": \"Customer or Software not found\"}");
        }
        if (cust.isArchived() || !cust.isEnabled()) {
            return ResponseEntity.badRequest().body("{\"error\": \"Customer must be active and enabled before creating customer templates\"}");
        }
        if (sw.isArchived() || !sw.isEnabled()) {
            return ResponseEntity.badRequest().body("{\"error\": \"Software must be active and enabled before creating customer templates\"}");
        }
        if (!customerSoftwareAccessRepository.existsByCustomerIdAndSoftwareIdAndEnabledTrue(customerId, softwareId)) {
            return ResponseEntity.badRequest().body("{\"error\": \"Assign and enable this software for the customer before creating a customer template\"}");
        }

        String name = request.get("name") != null ? request.get("name").toString() : null;
        String description = request.get("description") != null ? request.get("description").toString() : null;

        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("{\"error\": \"Name is required\"}");
        }

        CustomerTemplate def = new CustomerTemplate(cust, sw, name.trim(), description);
        try {
            def.setFileFormat(parseFormat(request.get("fileFormat") != null ? request.get("fileFormat").toString() : null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"error\": \"Unsupported fileFormat\"}");
        }
        if (request.containsKey("hasHeaderRow")) {
            def.setHasHeaderRow(Boolean.parseBoolean(String.valueOf(request.get("hasHeaderRow"))));
        }
        if (request.containsKey("enabled")) {
            def.setEnabled(Boolean.parseBoolean(String.valueOf(request.get("enabled"))));
        }
        boolean replaceActiveTemplate = Boolean.parseBoolean(String.valueOf(request.getOrDefault("replaceActiveTemplate", false)));
        String activeConflict = activeTemplateConflictMessage(def, null);
        if (activeConflict != null && !replaceActiveTemplate) {
            return ResponseEntity.badRequest().body(Map.of("error", activeConflict));
        }
        if (replaceActiveTemplate) {
            disableConflictingActiveTemplates(def, null);
        }
        def = templateRepository.save(def);

        return ResponseEntity.status(HttpStatus.CREATED).body(def);
    }

    @PostMapping("/software/{softwareId}/templates")
    @Transactional
    public ResponseEntity<?> createGlobalTemplate(@PathVariable UUID softwareId,
                                                  @RequestBody Map<String, Object> request) {
        AppUser currentUser = getCurrentUser();
        if (!authUtil.isSuperAdmin(currentUser) && !authUtil.isSecurityOperator(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only administrators or security operators can manage global templates\"}");
        }

        SecuritySoftware sw = softwareRepository.findById(softwareId).orElse(null);
        if (sw == null) {
            return ResponseEntity.badRequest().body("{\"error\": \"Software not found\"}");
        }

        String name = request.get("name") != null ? request.get("name").toString() : null;
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("{\"error\": \"Name is required\"}");
        }

        CustomerTemplate def = new CustomerTemplate(null, sw, name.trim(), request.get("description") != null ? request.get("description").toString() : null);
        try {
            def.setFileFormat(parseFormat(request.get("fileFormat") != null ? request.get("fileFormat").toString() : null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"error\": \"Unsupported fileFormat\"}");
        }
        if (request.containsKey("hasHeaderRow")) {
            def.setHasHeaderRow(Boolean.parseBoolean(String.valueOf(request.get("hasHeaderRow"))));
        }
        if (request.containsKey("enabled")) {
            def.setEnabled(Boolean.parseBoolean(String.valueOf(request.get("enabled"))));
        }
        boolean replaceActiveTemplate = Boolean.parseBoolean(String.valueOf(request.getOrDefault("replaceActiveTemplate", false)));
        String activeConflict = activeTemplateConflictMessage(def, null);
        if (activeConflict != null && !replaceActiveTemplate) {
            return ResponseEntity.badRequest().body(Map.of("error", activeConflict));
        }
        if (replaceActiveTemplate) {
            disableConflictingActiveTemplates(def, null);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(templateRepository.save(def));
    }

    // Upload sample file (up to 200MB limit configured in application properties usually)
    @PostMapping("/templates/{templateId}/sample")
    public ResponseEntity<?> uploadSample(@PathVariable UUID templateId,
                                          @RequestParam("file") MultipartFile file,
                                          @RequestParam(value = "format", defaultValue = "CSV") String formatStr,
                                          @RequestParam(value = "hasHeader", defaultValue = "true") boolean hasHeader) {
        
        CustomerTemplate template = templateRepository.findById(templateId).orElse(null);
        if (template == null) return ResponseEntity.notFound().build();

        AppUser currentUser = getCurrentUser();
        if (!canManageTemplate(currentUser, template)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Enums.FileFormat format;
        try {
            format = Enums.FileFormat.valueOf(formatStr.toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"error\": \"Unsupported format\"}");
        }

        List<String> headers = new ArrayList<>();
        List<String> firstDataRow = new ArrayList<>();
        String savedFilePath = null;

        try {
            // Save file using a server-generated basename and contained upload path.
            Path targetLocation = safeTemplateUploadPath(template, file);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            savedFilePath = targetLocation.toString();

            // Extract headers and one sample row without materializing the whole file.
            try (InputStream is = Files.newInputStream(targetLocation)) {
                if (format == Enums.FileFormat.CSV || format == Enums.FileFormat.TSV || format == Enums.FileFormat.PSV) {
                    CSVFormat csvFormat = CSVFormat.DEFAULT;
                    if (format == Enums.FileFormat.TSV) csvFormat = csvFormat.withDelimiter('\t');
                    else if (format == Enums.FileFormat.PSV) csvFormat = csvFormat.withDelimiter('|');

                    try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                         CSVParser parser = new CSVParser(reader, csvFormat)) {
                        Iterator<CSVRecord> iterator = parser.iterator();
                        if (iterator.hasNext()) {
                            CSVRecord first = iterator.next();
                            if (hasHeader) {
                                for (int i = 0; i < first.size(); i++) {
                                    headers.add(first.get(i));
                                }
                                if (iterator.hasNext()) {
                                    CSVRecord data = iterator.next();
                                    for (int i = 0; i < data.size(); i++) firstDataRow.add(data.get(i));
                                }
                            } else {
                                for (int i = 0; i < first.size(); i++) {
                                    headers.add("Column_" + i);
                                    firstDataRow.add(first.get(i));
                                }
                            }
                        }
                    }
                } else if (format == Enums.FileFormat.XLS || format == Enums.FileFormat.XLSX) {
                    try (Workbook workbook = WorkbookFactory.create(is)) {
                        Sheet sheet = workbook.getSheetAt(0);
                        Row row = sheet.getRow(0);
                        if (row != null) {
                            if (hasHeader) {
                                for (int i = 0; i < row.getLastCellNum(); i++) {
                                    String val = getCellValueAsString(row.getCell(i));
                                    headers.add(val.isEmpty() ? "Column_" + i : val);
                                }
                            } else {
                                for (int i = 0; i < row.getLastCellNum(); i++) {
                                    headers.add("Column_" + i);
                                    firstDataRow.add(getCellValueAsString(row.getCell(i)));
                                }
                            }
                        }
                        if (hasHeader) {
                            Row dataRow = sheet.getRow(1);
                            if (dataRow != null) {
                                for (int i = 0; i < dataRow.getLastCellNum(); i++) {
                                    firstDataRow.add(getCellValueAsString(dataRow.getCell(i)));
                                }
                            }
                        }
                    }
                }
            }
            
            // Update template with file path
            template.setSampleFilePath(savedFilePath);
            template.setFileFormat(format);
            template.setHasHeaderRow(hasHeader);
            
            // Initialize draft mapping JSON if it's currently empty
            if (template.getColumnMappingJson() == null || template.getColumnMappingJson().isEmpty()) {
                List<Map<String, Object>> draftMappings = new ArrayList<>();
                for (int i = 0; i < headers.size(); i++) {
                    Map<String, Object> draft = new HashMap<>();
                    draft.put("sourceColumnIndex", i);
                    draft.put("sourceColumnName", headers.get(i));
                    draft.put("targetFieldName", ""); 
                    draft.put("transformations", new ArrayList<Map<String, Object>>()); // E.g., { "action": "SET_NULL" }
                    draftMappings.add(draft);
                }
                Map<String, Object> draftDocument = new HashMap<>();
                draftDocument.put("metadata", Map.of("status", "draft", "version", 1));
                draftDocument.put("mappings", draftMappings);
                template.setColumnMappingJson(objectMapper.writeValueAsString(draftDocument));
            }

            templateRepository.save(template);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"error\": \"Failed to process sample file: " + e.getMessage() + "\"}");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("headers", headers);
        response.put("firstDataRow", firstDataRow);
        response.put("savedFilePath", savedFilePath);
        try {
            if (template.getColumnMappingJson() != null) {
                Object mappingDocument = objectMapper.readValue(template.getColumnMappingJson(), Object.class);
                if (mappingDocument instanceof Map<?, ?> document && document.get("mappings") instanceof List<?> mappings) {
                    response.put("currentMapping", mappings);
                } else {
                    response.put("currentMapping", mappingDocument);
                }
            } else {
                response.put("currentMapping", new ArrayList<>());
            }
        } catch (JsonProcessingException e) {
            response.put("currentMapping", new ArrayList<>());
        }
        
        return ResponseEntity.ok(response);
    }

    private SampleRows readSampleRows(Path samplePath, Enums.FileFormat format, boolean hasHeader, int maxRows) throws Exception {
        String[] headers = null;
        List<String[]> rows = new ArrayList<>();
        try (InputStream is = Files.newInputStream(samplePath)) {
            if (format == Enums.FileFormat.CSV || format == Enums.FileFormat.TSV || format == Enums.FileFormat.PSV) {
                CSVFormat csvFormat = CSVFormat.DEFAULT;
                if (format == Enums.FileFormat.TSV) csvFormat = csvFormat.withDelimiter('\t');
                else if (format == Enums.FileFormat.PSV) csvFormat = csvFormat.withDelimiter('|');

                try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                     CSVParser parser = new CSVParser(reader, csvFormat)) {
                    Iterator<CSVRecord> iterator = parser.iterator();
                    if (hasHeader && iterator.hasNext()) {
                        headers = csvRecordToArray(iterator.next());
                    }
                    while (iterator.hasNext() && rows.size() < maxRows) {
                        rows.add(csvRecordToArray(iterator.next()));
                    }
                }
            } else {
                try (Workbook workbook = WorkbookFactory.create(is)) {
                    Sheet sheet = workbook.getSheetAt(0);
                    int startIdx = 0;
                    if (hasHeader) {
                        Row headerRow = sheet.getRow(0);
                        if (headerRow != null) {
                            headers = rowToArray(headerRow);
                        }
                        startIdx = 1;
                    }
                    for (int i = startIdx; i <= sheet.getLastRowNum() && rows.size() < maxRows; i++) {
                        Row row = sheet.getRow(i);
                        if (row != null) {
                            rows.add(rowToArray(row));
                        }
                    }
                }
            }
        }
        return new SampleRows(headers, rows);
    }

    private String[] csvRecordToArray(CSVRecord record) {
        String[] row = new String[record.size()];
        for (int i = 0; i < record.size(); i++) {
            row[i] = record.get(i);
        }
        return row;
    }

    private String[] rowToArray(Row row) {
        String[] values = new String[row.getLastCellNum() > 0 ? row.getLastCellNum() : 0];
        for (int i = 0; i < values.length; i++) {
            values[i] = getCellValueAsString(row.getCell(i));
        }
        return values;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toString() + "Z"
                    : java.math.BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue();
                } catch (Exception e) {
                    yield java.math.BigDecimal.valueOf(cell.getNumericCellValue()).toPlainString();
                }
            }
            default -> "";
        };
    }

    private record SampleRows(String[] headers, List<String[]> rows) {}

    // Save column mapping json
    @PutMapping("/templates/{templateId}/mapping")
    @Transactional
    public ResponseEntity<?> saveTemplateMapping(@PathVariable UUID templateId, @RequestBody Map<String, Object> mappingDocument) {
        CustomerTemplate template = templateRepository.findById(templateId).orElse(null);
        if (template == null) return ResponseEntity.notFound().build();

        AppUser currentUser = getCurrentUser();
        if (!canManageTemplate(currentUser, template)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            MappingEngineService.MappingConfiguration validation = mappingEngineService.validateMappingDocument(mappingDocument, false);
            if (!validation.ready()) {
                return ResponseEntity.badRequest().body(Map.of("error", validation.errorMessage()));
            }
            String json = objectMapper.writeValueAsString(mappingDocument);
            template.setColumnMappingJson(json);
            templateRepository.save(template);
            return ResponseEntity.ok(template);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body("{\"error\": \"Invalid mapping JSON\"}");
        }
    }

    @PostMapping("/templates/{templateId}/preview")
    public ResponseEntity<?> previewTemplateMapping(@PathVariable UUID templateId,
                                                   @RequestBody Map<String, Object> mappingDocument) {
        CustomerTemplate template = templateRepository.findById(templateId).orElse(null);
        if (template == null) return ResponseEntity.notFound().build();

        AppUser currentUser = getCurrentUser();
        if (!canManageTemplate(currentUser, template)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (template.getSampleFilePath() == null || template.getSampleFilePath().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Upload and extract a sample file before previewing mapping rules."));
        }

        MappingEngineService.MappingConfiguration validation = mappingEngineService.validateMappingDocument(mappingDocument, false);
        if (!validation.ready()) {
            return ResponseEntity.badRequest().body(Map.of("error", validation.errorMessage()));
        }

        try {
            SampleRows sampleRows = readSampleRows(
                    Paths.get(template.getSampleFilePath()),
                    template.getFileFormat(),
                    template.isHasHeaderRow(),
                    25
            );
            MappingEngineService.MappingPlan plan = mappingEngineService.compile(validation.mappings(), sampleRows.headers(), template.isHasHeaderRow());
            List<Map<String, Object>> rows = new ArrayList<>();
            int passed = 0;
            int failed = 0;
            for (int i = 0; i < sampleRows.rows().size(); i++) {
                MappingEngineService.RowProcessingResult result = mappingEngineService.processDataRow(sampleRows.rows().get(i), plan, null, null);
                if (result.valid()) passed++;
                else failed++;
                rows.add(Map.of(
                        "rowNumber", i + 1,
                        "status", result.valid() ? "PASSED" : "FAILED",
                        "error", result.errorReason(),
                        "fields", result.fieldPreviews()
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "testedRows", sampleRows.rows().size(),
                    "passedRows", passed,
                    "failedRows", failed,
                    "headers", sampleRows.headers() != null ? sampleRows.headers() : List.of(),
                    "rows", rows
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to preview mapping: " + e.getMessage()));
        }
    }

    @PutMapping("/templates/{templateId}")
    @Transactional
    public ResponseEntity<?> updateTemplate(@PathVariable UUID templateId, @RequestBody Map<String, Object> request) {
        CustomerTemplate template = templateRepository.findById(templateId).orElse(null);
        if (template == null) return ResponseEntity.notFound().build();

        AppUser currentUser = getCurrentUser();
        if (!canManageTemplate(currentUser, template)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Object name = request.get("name");
        if (name instanceof String value && !value.trim().isEmpty()) {
            template.setName(value.trim());
        }
        Object description = request.get("description");
        if (description instanceof String value) {
            template.setDescription(value);
        }
        Object fileFormat = request.get("fileFormat");
        if (fileFormat instanceof String value) {
            try {
                template.setFileFormat(parseFormat(value));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body("{\"error\": \"Unsupported fileFormat\"}");
            }
        }
        Object hasHeaderRow = request.get("hasHeaderRow");
        if (hasHeaderRow instanceof Boolean value) {
            template.setHasHeaderRow(value);
        }
        Object enabled = request.get("enabled");
        if (enabled instanceof Boolean value) {
            template.setEnabled(value);
        }
        Object archived = request.get("archived");
        if (archived instanceof Boolean value) {
            template.setArchived(value);
            template.setEnabled(false);
            template.setArchivedAt(value ? OffsetDateTime.now() : null);
            template.setArchivedBy(value ? currentUser.getUsername() : null);
        }

        boolean replaceActiveTemplate = Boolean.parseBoolean(String.valueOf(request.getOrDefault("replaceActiveTemplate", false)));
        String activeConflict = activeTemplateConflictMessage(template, template.getId());
        if (activeConflict != null && !replaceActiveTemplate) {
            return ResponseEntity.badRequest().body(Map.of("error", activeConflict));
        }
        if (replaceActiveTemplate) {
            disableConflictingActiveTemplates(template, template.getId());
        }

        return ResponseEntity.ok(templateRepository.save(template));
    }

    private String activeTemplateConflictMessage(CustomerTemplate candidate, UUID ignoreTemplateId) {
        if (!candidate.isEnabled() || candidate.isArchived() || candidate.getSoftware() == null) {
            return null;
        }

        boolean customerScoped = candidate.getCustomer() != null;
        boolean conflict = templateRepository.findBySoftwareId(candidate.getSoftware().getId()).stream()
                .filter(template -> ignoreTemplateId == null || !template.getId().equals(ignoreTemplateId))
                .filter(template -> template.isEnabled() && !template.isArchived())
                .filter(template -> {
                    if (customerScoped) {
                        return template.getCustomer() != null && template.getCustomer().getId().equals(candidate.getCustomer().getId());
                    }
                    return template.getCustomer() == null;
                })
                .findAny()
                .isPresent();

        if (!conflict) {
            return null;
        }

        String scope = customerScoped ? "this customer and software" : "this software";
        return "Only one active template is allowed for " + scope + ". Disable the current active template before enabling this one.";
    }

    private void disableConflictingActiveTemplates(CustomerTemplate candidate, UUID ignoreTemplateId) {
        if (!candidate.isEnabled() || candidate.isArchived() || candidate.getSoftware() == null) {
            return;
        }

        boolean customerScoped = candidate.getCustomer() != null;
        templateRepository.findBySoftwareId(candidate.getSoftware().getId()).stream()
                .filter(template -> ignoreTemplateId == null || !template.getId().equals(ignoreTemplateId))
                .filter(template -> template.isEnabled() && !template.isArchived())
                .filter(template -> {
                    if (customerScoped) {
                        return template.getCustomer() != null && template.getCustomer().getId().equals(candidate.getCustomer().getId());
                    }
                    return template.getCustomer() == null;
                })
                .forEach(template -> {
                    template.setEnabled(false);
                    templateRepository.save(template);
                });
    }

    @DeleteMapping("/templates/{templateId}")
    public ResponseEntity<?> deleteTemplate(@PathVariable UUID templateId) {
        CustomerTemplate template = templateRepository.findById(templateId).orElse(null);
        if (template == null) return ResponseEntity.notFound().build();

        AppUser currentUser = getCurrentUser();
        if (!canManageTemplate(currentUser, template)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        template.setArchived(true);
        template.setEnabled(false);
        template.setArchivedAt(OffsetDateTime.now());
        template.setArchivedBy(currentUser.getUsername());
        return ResponseEntity.ok(templateRepository.save(template));
    }

    // Download the sample file
    @GetMapping("/templates/{templateId}/sample")
    public ResponseEntity<Resource> downloadSample(@PathVariable UUID templateId) {
        CustomerTemplate template = templateRepository.findById(templateId).orElse(null);
        if (template == null || template.getSampleFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        AppUser currentUser = getCurrentUser();
        if (template.getCustomer() != null && !authUtil.canAccessCustomer(currentUser, template.getCustomer().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            Path file = Paths.get(template.getSampleFilePath());
            Resource resource = new UrlResource(file.toUri());

            if (resource.exists() || resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
