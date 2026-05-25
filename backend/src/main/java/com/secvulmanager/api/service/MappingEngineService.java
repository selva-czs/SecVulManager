package com.secvulmanager.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secvulmanager.api.model.Customer;
import com.secvulmanager.api.model.CustomerTemplate;
import com.secvulmanager.api.model.Enums;
import com.secvulmanager.api.model.UploadDetails;
import com.secvulmanager.api.model.VulnerabilityFinding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MappingEngineService {

    private final DestinationSchemaService schemaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MappingEngineService(DestinationSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    public MappingConfiguration loadMappingConfiguration(CustomerTemplate template) {
        if (!hasText(template.getColumnMappingJson())) {
            return MappingConfiguration.failed("Template has no saved mapping. Open the mapper, map required fields, and save the template.");
        }

        try {
            Map<String, Object> mappingDocument = objectMapper.readValue(template.getColumnMappingJson(), new TypeReference<Map<String, Object>>() {});
            return validateMappingDocument(mappingDocument, true);
        } catch (Exception e) {
            return MappingConfiguration.failed("Template mapping JSON is invalid: " + e.getMessage());
        }
    }

    public MappingConfiguration validateMappingDocument(Map<String, Object> mappingDocument, boolean requireReady) {
        Object metadataObject = mappingDocument.get("metadata");
        if (!(metadataObject instanceof Map<?, ?> metadata)) {
            return MappingConfiguration.failed("Template mapping metadata is missing. Save the template from the mapper before ingestion.");
        }

        String status = Objects.toString(metadata.get("status"), "");
        if (requireReady && !"ready".equalsIgnoreCase(status)) {
            return MappingConfiguration.failed("Template mapping is not ready. Complete required mappings and save the template before ingestion.");
        }

        Object mappingRows = mappingDocument.get("mappings");
        if (!(mappingRows instanceof List<?>)) {
            return MappingConfiguration.failed("Template mapping rows are missing. Save the template from the mapper before ingestion.");
        }

        List<Map<String, Object>> mappings = objectMapper.convertValue(mappingRows, new TypeReference<List<Map<String, Object>>>() {});
        if (requireReady && mappings.isEmpty()) {
            return MappingConfiguration.failed("Template has no mapped columns. Map required fields and save before ingestion.");
        }

        Set<String> mappedTargets = new HashSet<>();
        for (Map<String, Object> mapping : mappings) {
            String target = Objects.toString(mapping.get("targetFieldName"), "").trim();
            if (!target.isEmpty()) {
                if (schemaService.field(target) == null) {
                    return MappingConfiguration.failed("Unsupported destination field in mapping: " + target);
                }
                mappedTargets.add(target);
            }
            for (Map<String, Object> transform : readTransforms(mapping)) {
                String action = Objects.toString(transform.get("action"), "");
                try {
                    Enums.TransformationType.valueOf(action.toUpperCase(Locale.ROOT));
                } catch (Exception e) {
                    return MappingConfiguration.failed("Unsupported transform action in mapping: " + action);
                }
            }
        }
        if (requireReady && !mappedTargets.contains("issue_title")) {
            return MappingConfiguration.failed("Required destination field Issue Title is not mapped. Save as draft or map it before ingestion.");
        }

        return MappingConfiguration.ready(mappings);
    }

    public MappingPlan compile(List<Map<String, Object>> mappings, String[] headers, boolean hasHeaderRow) {
        Map<String, Integer> headerIndexes = new HashMap<>();
        if (hasHeaderRow && headers != null) {
            for (int i = 0; i < headers.length; i++) {
                if (headers[i] != null) {
                    headerIndexes.put(headers[i].trim().toLowerCase(Locale.ROOT), i);
                }
            }
        }

        List<PreparedMapping> preparedMappings = new ArrayList<>();
        int maxSourceColumnIndex = -1;
        for (Map<String, Object> mapping : mappings) {
            String targetField = Objects.toString(mapping.get("targetFieldName"), "").trim();
            if (targetField.isEmpty()) {
                continue;
            }

            String sourceColumnName = Objects.toString(mapping.get("sourceColumnName"), "");
            Integer sourceColumnIndex = null;
            if (hasHeaderRow && !headerIndexes.isEmpty() && hasText(sourceColumnName)) {
                sourceColumnIndex = headerIndexes.get(sourceColumnName.trim().toLowerCase(Locale.ROOT));
            }
            if (sourceColumnIndex == null) {
                sourceColumnIndex = parseSourceColumnIndex(mapping, sourceColumnName);
            }
            if (sourceColumnIndex != null) {
                maxSourceColumnIndex = Math.max(maxSourceColumnIndex, sourceColumnIndex);
            }

            List<Enums.TransformationType> transformations = readTransforms(mapping).stream()
                    .map(transform -> Enums.TransformationType.valueOf(Objects.toString(transform.get("action"), "").toUpperCase(Locale.ROOT)))
                    .toList();

            String targetDataType = Objects.toString(
                    mapping.get("targetDataType"),
                    Optional.ofNullable(schemaService.field(targetField)).map(DestinationSchemaService.DestinationField::type).orElse("STRING")
            );

            preparedMappings.add(new PreparedMapping(
                    targetField,
                    sourceColumnName,
                    sourceColumnIndex,
                    targetDataType,
                    transformations,
                    readEmptySourcePolicy(mapping),
                    Objects.toString(mapping.get("forceValue"), ""),
                    Objects.toString(mapping.get("conversionType"), "NONE"),
                    Objects.toString(mapping.get("conversionErrorMode"), "FAIL_ROW"),
                    Objects.toString(mapping.get("conversionErrorValue"), "")
            ));
        }
        return new MappingPlan(preparedMappings, maxSourceColumnIndex);
    }

    public RowProcessingResult processDataRow(String[] rawRow, MappingPlan plan, UploadDetails runLog, Customer customer) {
        StringBuilder errorReasons = new StringBuilder();
        VulnerabilityFinding finding = new VulnerabilityFinding();
        if (runLog != null) {
            finding.setUpload(runLog);
        }
        if (customer != null) {
            finding.setCustomer(customer);
        }

        boolean rowIsValid = true;
        List<FieldPreview> fieldPreviews = new ArrayList<>();
        for (PreparedMapping mapping : plan.mappings()) {
            String rawValue = null;
            Integer colIdx = mapping.sourceColumnIndex();
            if (colIdx != null && colIdx >= 0 && colIdx < rawRow.length) {
                rawValue = rawRow[colIdx];
            }

            try {
                String convertedValue = applyMappingRules(rawValue, mapping);
                if (convertedValue != null && !convertedValue.trim().isEmpty()) {
                    bindField(finding, mapping.targetField(), convertedValue);
                }
                fieldPreviews.add(new FieldPreview(mapping.targetField(), mapping.sourceColumnName(), rawValue, convertedValue, "OK", ""));
            } catch (Exception ex) {
                rowIsValid = false;
                String error = mapping.targetField()
                        + " from "
                        + (hasText(mapping.sourceColumnName()) ? mapping.sourceColumnName() : "source column")
                        + " failed: "
                        + ex.getMessage();
                errorReasons.append(error).append("; ");
                fieldPreviews.add(new FieldPreview(mapping.targetField(), mapping.sourceColumnName(), rawValue, null, "FAILED", error));
            }
        }

        if (finding.getIssueTitle() == null || finding.getIssueTitle().trim().isEmpty()) {
            rowIsValid = false;
            String error = "issue_title is a required target standard field";
            errorReasons.append(error).append("; ");
            fieldPreviews.add(new FieldPreview("issue_title", "", "", null, "FAILED", error));
        }

        return new RowProcessingResult(rowIsValid, finding, errorReasons.toString(), fieldPreviews);
    }

    public String[] buildErrorHeaders(String[] headers) {
        String[] errorFileHeaders = Arrays.copyOf(headers, headers.length + 1);
        errorFileHeaders[headers.length] = "ingestion_error_reason";
        return errorFileHeaders;
    }

    public String[] buildGeneratedErrorHeaders(int rowLength, MappingPlan plan) {
        int maxCols = Math.max(0, rowLength);
        if (plan != null && plan.maxSourceColumnIndex() >= 0) {
            maxCols = Math.max(maxCols, plan.maxSourceColumnIndex() + 1);
        }
        String[] errorFileHeaders = new String[maxCols + 1];
        for (int i = 0; i < maxCols; i++) {
            errorFileHeaders[i] = "Column_" + i;
        }
        errorFileHeaders[maxCols] = "ingestion_error_reason";
        return errorFileHeaders;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readTransforms(Map<String, Object> mapping) {
        Object transforms = mapping.get("transformations");
        if (transforms instanceof List<?>) {
            return (List<Map<String, Object>>) transforms;
        }
        return Collections.emptyList();
    }

    private Integer parseSourceColumnIndex(Map<String, Object> mapping, String sourceColumnName) {
        Object rawIndex = mapping.get("sourceColumnIndex");
        if (rawIndex instanceof Number number) {
            return number.intValue();
        }
        if (rawIndex instanceof String indexText) {
            try {
                return Integer.parseInt(indexText);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (sourceColumnName != null && sourceColumnName.startsWith("Column_")) {
            try {
                return Integer.parseInt(sourceColumnName.substring("Column_".length()));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String applyMappingRules(String rawValue, PreparedMapping mapping) {
        String value = rawValue;
        for (Enums.TransformationType transform : mapping.transformations()) {
            value = applyTransformation(value, transform);
        }

        if (hasText(mapping.forceValue())) {
            value = mapping.forceValue();
        } else if (mapping.sourceColumnIndex() == null && !hasText(mapping.sourceColumnName())) {
            value = applyEmptySourcePolicy(mapping);
        } else if (!hasText(value)) {
            value = applyEmptySourcePolicy(mapping);
        }

        if ("NONE".equalsIgnoreCase(mapping.conversionType()) || !hasText(value)) {
            return value;
        }

        try {
            return convertValue(value, mapping.conversionType(), mapping.targetDataType());
        } catch (Exception conversionError) {
            String errorMode = mapping.conversionErrorMode();
            if ("SET_EMPTY".equals(errorMode)) return "";
            if ("SET_CUSTOM".equals(errorMode)) return hasText(mapping.conversionErrorValue()) ? mapping.conversionErrorValue() : "";
            if ("SET_NULL".equals(errorMode)) return null;
            if ("FAIL_ROW".equals(errorMode)) {
                throw new IllegalArgumentException("conversion " + mapping.conversionType() + " failed for value \"" + value + "\" using failure mode FAIL_ROW: " + conversionError.getMessage());
            }
            throw new IllegalArgumentException("Unsupported conversion failure mode " + errorMode + " for " + mapping.sourceColumnName() + " -> " + mapping.targetField());
        }
    }

    private EmptySourcePolicy readEmptySourcePolicy(Map<String, Object> mapping) {
        Object policyObject = mapping.get("emptySourcePolicy");
        if (policyObject instanceof Map<?, ?> policy) {
            String mode = Objects.toString(policy.get("mode"), "LEAVE_EMPTY");
            String value = Objects.toString(policy.get("defaultValue"), "");
            return new EmptySourcePolicy(normalizeEmptySourceMode(mode), value);
        }
        String legacyDefaultValue = Objects.toString(mapping.get("defaultValue"), "");
        if (hasText(legacyDefaultValue)) {
            return new EmptySourcePolicy("USE_DEFAULT", legacyDefaultValue);
        }
        return new EmptySourcePolicy("LEAVE_EMPTY", "");
    }

    private String normalizeEmptySourceMode(String mode) {
        String normalized = Objects.toString(mode, "").trim().toUpperCase(Locale.ROOT);
        if (Set.of("LEAVE_EMPTY", "SET_NULL", "USE_DEFAULT", "FAIL_ROW").contains(normalized)) {
            return normalized;
        }
        return "LEAVE_EMPTY";
    }

    private String applyEmptySourcePolicy(PreparedMapping mapping) {
        EmptySourcePolicy policy = mapping.emptySourcePolicy();
        return switch (policy.mode()) {
            case "SET_NULL" -> null;
            case "USE_DEFAULT" -> policy.defaultValue();
            case "FAIL_ROW" -> throw new IllegalArgumentException("source value is empty and empty-source policy is FAIL_ROW");
            case "LEAVE_EMPTY" -> "";
            default -> "";
        };
    }

    private String applyTransformation(String value, Enums.TransformationType transform) {
        if (value == null) return null;
        return switch (transform) {
            case TRIM -> value.trim();
            case TO_UPPER -> value.toUpperCase(Locale.ROOT);
            case TO_LOWER -> value.toLowerCase(Locale.ROOT);
            case REMOVESPACES -> value.replaceAll("\\s+", "");
        };
    }

    private String convertValue(String value, String conversionType, String targetDataType) {
        String text = value == null ? "" : value.trim();
        return switch (conversionType.toUpperCase(Locale.ROOT)) {
            case "TO_STRING" -> value;
            case "TO_NUMBER" -> {
                String numeric = text.replace(",", "");
                BigDecimal number = new BigDecimal(numeric);
                if ("INTEGER".equalsIgnoreCase(targetDataType) && number.stripTrailingZeros().scale() > 0) {
                    throw new IllegalArgumentException("Value is not a whole number");
                }
                yield "INTEGER".equalsIgnoreCase(targetDataType) ? String.valueOf(number.intValueExact()) : number.toPlainString();
            }
            case "TO_DATE" -> {
                try {
                    yield OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                } catch (Exception ignored) {
                    yield OffsetDateTime.parse(text + "T00:00:00+00:00").format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
                }
            }
            case "TO_BOOLEAN" -> {
                String normalized = text.toLowerCase(Locale.ROOT);
                if (Arrays.asList("true", "1", "yes", "y").contains(normalized)) yield "true";
                if (Arrays.asList("false", "0", "no", "n").contains(normalized)) yield "false";
                throw new IllegalArgumentException("Value is not boolean");
            }
            default -> throw new IllegalArgumentException("Unsupported conversion type " + conversionType);
        };
    }

    private void bindField(VulnerabilityFinding finding, String fieldName, String value) {
        switch (fieldName) {
            case "severity" -> bindSeverity(finding, value);
            case "cvss_score" -> finding.setCvssScore(new BigDecimal(value.trim()));
            case "cvss_vector" -> finding.setCvssVector(value);
            case "cve_id" -> finding.setCveId(value.trim());
            case "oid" -> finding.setOid(value);
            case "issue_title" -> finding.setIssueTitle(value);
            case "summary" -> finding.setSummary(value);
            case "impact" -> finding.setImpact(value);
            case "solution" -> finding.setSolution(value);
            case "vulnerability_insight" -> finding.setVulnerabilityInsight(value);
            case "vulnerability_detection_result" -> finding.setVulnerabilityDetectionResult(value);
            case "vulnerability_detection_method" -> finding.setVulnerabilityDetectionMethod(value);
            case "affected_devices" -> finding.setAffectedDevices(value);
            case "number_of_devices" -> finding.setNumberOfDevices(Integer.parseInt(value.trim()));
            case "references_info" -> finding.setReferencesInfo(value);
            case "known_exploited" -> finding.setKnownExploited(parseBoolean(value));
            case "known_ransomware_campaign" -> finding.setKnownRansomwareCampaign(parseBoolean(value));
            case "last_detected_at" -> {
                try {
                    finding.setLastDetectedAt(OffsetDateTime.parse(value.trim(), DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                } catch (Exception e) {
                    finding.setLastDetectedAt(OffsetDateTime.parse(value.trim() + "T00:00:00+00:00"));
                }
            }
            default -> {
            }
        }
    }

    private void bindSeverity(VulnerabilityFinding finding, String value) {
        try {
            finding.setSeverity(Enums.SeverityLevel.valueOf(value.toUpperCase(Locale.ROOT).trim()));
        } catch (Exception e) {
            String clean = value.toUpperCase(Locale.ROOT).trim();
            if (clean.contains("LOW")) finding.setSeverity(Enums.SeverityLevel.LOW);
            else if (clean.contains("HIGH")) finding.setSeverity(Enums.SeverityLevel.HIGH);
            else if (clean.contains("CRIT")) finding.setSeverity(Enums.SeverityLevel.CRITICAL);
            else finding.setSeverity(Enums.SeverityLevel.MEDIUM);
        }
    }

    private boolean parseBoolean(String value) {
        if (value == null) return false;
        String val = value.trim().toLowerCase(Locale.ROOT);
        return val.equals("true") || val.equals("1") || val.equals("yes") || val.equals("y");
    }

    private boolean hasText(Object value) {
        return value != null && !value.toString().trim().isEmpty();
    }

    public record MappingConfiguration(boolean ready, List<Map<String, Object>> mappings, String errorMessage) {
        static MappingConfiguration ready(List<Map<String, Object>> mappings) {
            return new MappingConfiguration(true, mappings, null);
        }

        static MappingConfiguration failed(String errorMessage) {
            return new MappingConfiguration(false, Collections.emptyList(), errorMessage);
        }
    }

    public record MappingPlan(List<PreparedMapping> mappings, int maxSourceColumnIndex) {}

    public record PreparedMapping(
            String targetField,
            String sourceColumnName,
            Integer sourceColumnIndex,
            String targetDataType,
            List<Enums.TransformationType> transformations,
            EmptySourcePolicy emptySourcePolicy,
            String forceValue,
            String conversionType,
            String conversionErrorMode,
            String conversionErrorValue
    ) {}

    public record EmptySourcePolicy(String mode, String defaultValue) {}

    public record RowProcessingResult(boolean valid, VulnerabilityFinding finding, String errorReason, List<FieldPreview> fieldPreviews) {}

    public record FieldPreview(String targetField, String sourceColumn, String sourceValue, String outputValue, String status, String error) {}
}
