package com.secvulmanager.api.model;

public class Enums {
    
    public enum FileFormat {
        CSV, TSV, PSV, XLS, XLSX
    }
    
    public enum UploadStatus {
        PROCESSING, SUCCESS, PARTIAL_FAILURE, FAILED
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
