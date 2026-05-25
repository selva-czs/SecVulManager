package com.secvulmanager.api.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "upload_details")
public class UploadDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "template_id")
    private CustomerTemplate template;

    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt = OffsetDateTime.now();

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Enums.UploadStatus status = Enums.UploadStatus.PROCESSING;

    @Column(name = "total_records", nullable = false)
    private int totalRecords = 0;

    @Column(name = "failed_records", nullable = false)
    private int failedRecords = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "error_log_path", columnDefinition = "TEXT")
    private String errorLogPath;

    @Column(name = "sample_file_path", columnDefinition = "TEXT")
    private String sampleFilePath;

    @Column(name = "is_active_snapshot", nullable = false)
    private boolean isActiveSnapshot = false;

    // Constructors
    public UploadDetails() {}

    public UploadDetails(Customer customer, CustomerTemplate template, String uploadedBy, String fileName) {
        this.customer = customer;
        this.template = template;
        this.uploadedBy = uploadedBy;
        this.fileName = fileName;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public CustomerTemplate getTemplate() {
        return template;
    }

    public void setTemplate(CustomerTemplate template) {
        this.template = template;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public OffsetDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(OffsetDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Enums.UploadStatus getStatus() {
        return status;
    }

    public void setStatus(Enums.UploadStatus status) {
        this.status = status;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    public int getFailedRecords() {
        return failedRecords;
    }

    public void setFailedRecords(int failedRecords) {
        this.failedRecords = failedRecords;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    public String getErrorLogPath() {
        return errorLogPath;
    }

    public void setErrorLogPath(String errorLogPath) {
        this.errorLogPath = errorLogPath;
    }

    public String getSampleFilePath() {
        return sampleFilePath;
    }

    public void setSampleFilePath(String sampleFilePath) {
        this.sampleFilePath = sampleFilePath;
    }

    public boolean isActiveSnapshot() {
        return isActiveSnapshot;
    }

    public void setActiveSnapshot(boolean activeSnapshot) {
        isActiveSnapshot = activeSnapshot;
    }
}
