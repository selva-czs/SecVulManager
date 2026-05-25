package com.secvulmanager.api.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DestinationSchemaService {

    private static final List<DestinationField> FIELDS = List.of(
            new DestinationField("issue_title", "Issue Title", "STRING", true, false),
            new DestinationField("severity", "Severity", "STRING", false, false),
            new DestinationField("cvss_score", "CVSS Score", "NUMERIC", false, true),
            new DestinationField("cvss_vector", "CVSS Vector", "STRING", false, true),
            new DestinationField("cve_id", "CVE ID", "STRING", false, true),
            new DestinationField("oid", "OID", "STRING", false, true),
            new DestinationField("summary", "Summary", "STRING", false, true),
            new DestinationField("impact", "Impact", "STRING", false, true),
            new DestinationField("solution", "Solution", "STRING", false, true),
            new DestinationField("vulnerability_insight", "Insight", "STRING", false, true),
            new DestinationField("vulnerability_detection_result", "Detection Result", "STRING", false, true),
            new DestinationField("vulnerability_detection_method", "Detection Method", "STRING", false, true),
            new DestinationField("affected_devices", "Affected Devices", "STRING", false, true),
            new DestinationField("number_of_devices", "Number of Devices", "INTEGER", false, true),
            new DestinationField("references_info", "References", "STRING", false, true),
            new DestinationField("known_exploited", "Known Exploited", "BOOLEAN", false, false),
            new DestinationField("known_ransomware_campaign", "Known Ransomware Campaign", "BOOLEAN", false, false),
            new DestinationField("last_detected_at", "Last Detected At", "DATE", false, true)
    );

    public List<DestinationField> fields() {
        return FIELDS;
    }

    public DestinationField field(String name) {
        return FIELDS.stream()
                .filter(field -> field.value().equals(name))
                .findFirst()
                .orElse(null);
    }

    public record DestinationField(String value, String label, String type, boolean required, boolean nullable) {}
}
