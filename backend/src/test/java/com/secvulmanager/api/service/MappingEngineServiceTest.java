package com.secvulmanager.api.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class MappingEngineServiceTest {

    private final MappingEngineService service = new MappingEngineService(new DestinationSchemaService());

    @Test
    void dateConversionNormalizesCommonVendorDateFormatsToIsoOffsetDateTime() {
        Map<String, String> examples = Map.of(
                "2026-05-25T08:15:30+05:30", "2026-05-25T08:15:30+05:30",
                "2026-05-25", "2026-05-25T00:00:00Z",
                "2026-05-25 08:15:30", "2026-05-25T08:15:30Z",
                "05/25/2026", "2026-05-25T00:00:00Z",
                "05/25/2026 08:15:30", "2026-05-25T08:15:30Z",
                "27-Mar-2026", "2026-03-27T00:00:00Z"
        );

        for (Map.Entry<String, String> example : examples.entrySet()) {
            MappingEngineService.RowProcessingResult result = processSingleMappedValue(
                    example.getKey(),
                    "last_detected_at",
                    "DATETIME",
                    "TO_DATE"
            );

            assertTrue(result.valid(), () -> "Expected valid row for " + example.getKey() + ": " + result.errorReason());
            assertEquals(example.getValue(), result.fieldPreviews().get(0).outputValue());
        }
    }

    @Test
    void numberConversionRemovesGroupingSeparatorsAndTrailingPercentWithoutScaling() {
        MappingEngineService.RowProcessingResult grouped = processSingleMappedValue(
                "1,234.50",
                "cvss_score",
                "DECIMAL",
                "TO_NUMBER"
        );
        MappingEngineService.RowProcessingResult percent = processSingleMappedValue(
                "12.5%",
                "cvss_score",
                "DECIMAL",
                "TO_NUMBER"
        );

        assertTrue(grouped.valid(), grouped::errorReason);
        assertEquals("1234.50", grouped.finding().getCvssScore().toPlainString());
        assertTrue(percent.valid(), percent::errorReason);
        assertEquals("12.5", percent.finding().getCvssScore().toPlainString());
    }

    @Test
    void blankLikeValuesUseEmptySourcePolicyBeforeConversion() {
        for (String blankLikeValue : List.of("N/A", "NA", "NULL", "-", "--")) {
            MappingEngineService.RowProcessingResult result = processSingleMappedValue(
                    blankLikeValue,
                    "summary",
                    "STRING",
                    "TO_NUMBER",
                    Map.of("mode", "SET_NULL")
            );

            assertTrue(result.valid(), () -> "Expected valid row for " + blankLikeValue + ": " + result.errorReason());
            assertNull(result.finding().getSummary());
        }
    }

    @Test
    void booleanConversionAcceptsCommonVendorBooleanValues() {
        for (String trueValue : List.of(
                "true",
                "1",
                "yes",
                "y",
                "enabled",
                "known",
                "This vulnerability is in the CISA's Known Exploited Vulnerabilities Catalog. Added on 2025-01-23"
        )) {
            MappingEngineService.RowProcessingResult result = processSingleMappedValue(
                    trueValue,
                    "known_exploited",
                    "BOOLEAN",
                    "TO_BOOLEAN"
            );

            assertTrue(result.valid(), () -> "Expected true boolean for " + trueValue + ": " + result.errorReason());
            assertTrue(result.finding().isKnownExploited());
        }

        for (String falseValue : List.of("false", "0", "no", "n", "disabled", "unknown", "not known", "none")) {
            MappingEngineService.RowProcessingResult result = processSingleMappedValue(
                    falseValue,
                    "known_exploited",
                    "BOOLEAN",
                    "TO_BOOLEAN"
            );

            assertTrue(result.valid(), () -> "Expected false boolean for " + falseValue + ": " + result.errorReason());
            assertFalse(result.finding().isKnownExploited());
        }
    }

    @Test
    void blankOptionalBooleanCellsDoNotFailRows() {
        for (String blankValue : List.of("", " ", "\u00A0", "N/A")) {
            MappingEngineService.RowProcessingResult result = processSingleMappedValue(
                    blankValue,
                    "known_ransomware_campaign",
                    "BOOLEAN",
                    "TO_BOOLEAN"
            );

            assertTrue(result.valid(), () -> "Expected blank optional boolean to be false for [" + blankValue + "]: " + result.errorReason());
            assertFalse(result.finding().isKnownRansomwareCampaign());
        }
    }

    @Test
    void readyMappingRejectsDuplicateDestinationFields() {
        MappingEngineService.MappingConfiguration result = service.validateMappingDocument(Map.of(
                "metadata", Map.of("status", "ready"),
                "mappings", List.of(
                        Map.of(
                                "sourceColumnIndex", 0,
                                "sourceColumnName", "Title A",
                                "targetFieldName", "issue_title",
                                "conversionType", "NONE",
                                "conversionErrorMode", "FAIL_ROW"
                        ),
                        Map.of(
                                "sourceColumnIndex", 1,
                                "sourceColumnName", "Title B",
                                "targetFieldName", "issue_title",
                                "conversionType", "NONE",
                                "conversionErrorMode", "FAIL_ROW"
                        )
                )
        ), true);

        assertFalse(result.ready());
        assertTrue(result.errorMessage().contains("Duplicate destination field"));
    }

    @Test
    void readyMappingRejectsInvalidConversionType() {
        MappingEngineService.MappingConfiguration result = service.validateMappingDocument(Map.of(
                "metadata", Map.of("status", "ready"),
                "mappings", List.of(Map.of(
                        "sourceColumnIndex", 0,
                        "sourceColumnName", "Title",
                        "targetFieldName", "issue_title",
                        "conversionType", "TO_MAGIC",
                        "conversionErrorMode", "FAIL_ROW"
                ))
        ), true);

        assertFalse(result.ready());
        assertTrue(result.errorMessage().contains("Unsupported conversion type"));
    }

    @Test
    void readyMappingRejectsNegativeSourceIndex() {
        MappingEngineService.MappingConfiguration result = service.validateMappingDocument(Map.of(
                "metadata", Map.of("status", "ready"),
                "mappings", List.of(Map.of(
                        "sourceColumnIndex", -1,
                        "sourceColumnName", "Title",
                        "targetFieldName", "issue_title",
                        "conversionType", "NONE",
                        "conversionErrorMode", "FAIL_ROW"
                ))
        ), true);

        assertFalse(result.ready());
        assertTrue(result.errorMessage().contains("Source column index cannot be negative"));
    }

    private MappingEngineService.RowProcessingResult processSingleMappedValue(
            String rawValue,
            String targetField,
            String targetDataType,
            String conversionType
    ) {
        return processSingleMappedValue(rawValue, targetField, targetDataType, conversionType, Map.of("mode", "LEAVE_EMPTY"));
    }

    private MappingEngineService.RowProcessingResult processSingleMappedValue(
            String rawValue,
            String targetField,
            String targetDataType,
            String conversionType,
            Map<String, Object> emptySourcePolicy
    ) {
        MappingEngineService.MappingPlan plan = service.compile(List.of(
                Map.of(
                        "sourceColumnIndex", 0,
                        "sourceColumnName", "Vendor Value",
                        "targetFieldName", targetField,
                        "targetDataType", targetDataType,
                        "conversionType", conversionType,
                        "conversionErrorMode", "FAIL_ROW",
                        "emptySourcePolicy", emptySourcePolicy
                ),
                Map.of(
                        "sourceColumnIndex", 1,
                        "sourceColumnName", "Title",
                        "targetFieldName", "issue_title",
                        "targetDataType", "STRING",
                        "conversionType", "NONE",
                        "conversionErrorMode", "FAIL_ROW"
                )
        ), new String[] {"Vendor Value", "Title"}, true);

        return service.processDataRow(new String[] {rawValue, "Required title"}, plan, null, null);
    }
}
