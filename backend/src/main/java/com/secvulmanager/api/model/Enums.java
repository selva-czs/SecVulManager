package com.secvulmanager.api.model;

public class Enums {
    
    public enum FileFormat {
        CSV, TSV, PSV, XLS, XLSX
    }
    
    public enum UploadStatus {
        PROCESSING, SUCCESS, PARTIAL_FAILURE, FAILED
    }

    public enum ProcessingStage {
        FILE_STORED,
        QUEUED,
        VALIDATING_TEMPLATE,
        READING_FILE,
        VALIDATING_HEADERS,
        PROCESSING_ROWS,
        WRITING_FAILED_ROWS,
        SAVING_FINDINGS,
        ACTIVATING_SNAPSHOT,
        COMPLETED,
        FAILED
    }

    public enum QueueMode {
        REJECT_IF_BUSY,
        QUEUE,
        FORCE_ACTIVATE_WHEN_DONE
    }
    
    public enum SeverityLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
    
    public enum TransformationType {
        TRIM, TO_UPPER, TO_LOWER, REMOVESPACES
    }
    
    public enum UserRole {
        SUPER_ADMIN, GLOBAL_OPERATOR, CUSTOMER_OPERATOR, SECURITY_OPERATOR
    }
}
