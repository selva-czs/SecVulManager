package com.secvulmanager.api.repository;

import com.secvulmanager.api.model.UploadDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UploadDetailsRepository extends JpaRepository<UploadDetails, UUID> {
    List<UploadDetails> findByCustomerIdOrderByUploadedAtDesc(UUID customerId);

    Optional<UploadDetails> findFirstByCustomerIdAndSoftwareIsNullAndIsActiveSnapshotTrueOrderByUploadedAtDesc(UUID customerId);

    @Query("SELECT u FROM UploadDetails u WHERE u.customer.id = :customerId AND u.software.id = :softwareId AND u.isActiveSnapshot = true")
    Optional<UploadDetails> findActiveSnapshotForCustomerAndSoftware(@Param("customerId") UUID customerId, @Param("softwareId") UUID softwareId);

    @Query("""
            SELECT u FROM UploadDetails u
            WHERE u.customer.id = :customerId
              AND u.status = com.secvulmanager.api.model.Enums.UploadStatus.PROCESSING
              AND u.processingStage <> com.secvulmanager.api.model.Enums.ProcessingStage.QUEUED
            ORDER BY u.startedAt ASC NULLS LAST, u.uploadedAt ASC
            """)
    List<UploadDetails> findRunningForCustomer(@Param("customerId") UUID customerId);

    @Query("""
            SELECT u FROM UploadDetails u
            WHERE u.status = com.secvulmanager.api.model.Enums.UploadStatus.PROCESSING
              AND u.processingStage = com.secvulmanager.api.model.Enums.ProcessingStage.QUEUED
            ORDER BY u.queuedAt ASC NULLS LAST, u.uploadedAt ASC
            """)
    List<UploadDetails> findQueuedUploads();

    @Query("""
            SELECT u FROM UploadDetails u
            WHERE u.customer.id = :customerId
              AND u.status = com.secvulmanager.api.model.Enums.UploadStatus.PROCESSING
              AND u.processingStage = com.secvulmanager.api.model.Enums.ProcessingStage.QUEUED
            ORDER BY u.queuedAt ASC NULLS LAST, u.uploadedAt ASC
            """)
    List<UploadDetails> findQueuedForCustomer(@Param("customerId") UUID customerId);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE upload_details queued
            SET processing_stage = 'FILE_STORED',
                started_at = now()
            WHERE queued.id = :uploadId
              AND queued.status = 'PROCESSING'
              AND queued.processing_stage = 'QUEUED'
              AND NOT EXISTS (
                  SELECT 1
                  FROM upload_details running
                  WHERE running.customer_id = queued.customer_id
                    AND running.status = 'PROCESSING'
                    AND running.processing_stage <> 'QUEUED'
              )
            """, nativeQuery = true)
    int claimQueuedUpload(@Param("uploadId") UUID uploadId);

    @Modifying
    @Query("""
            UPDATE UploadDetails u
            SET u.isActiveSnapshot = false
            WHERE u.customer.id = :customerId
              AND u.software.id = :softwareId
              AND u.id <> :uploadId
              AND u.isActiveSnapshot = true
            """)
    int clearActiveSnapshotsForSoftwareExcept(@Param("customerId") UUID customerId,
                                              @Param("softwareId") UUID softwareId,
                                              @Param("uploadId") UUID uploadId);

    List<UploadDetails> findByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    @Query("""
            SELECT u FROM UploadDetails u
            WHERE (:customerId IS NULL OR u.customer.id = :customerId)
              AND (:softwareId IS NULL OR u.software.id = :softwareId)
              AND (:templateId IS NULL OR u.template.id = :templateId)
              AND (:status IS NULL OR u.status = :status)
              AND (:activeSnapshot IS NULL OR u.isActiveSnapshot = :activeSnapshot)
            ORDER BY u.uploadedAt DESC
            """)
    List<UploadDetails> findHistory(
            @Param("customerId") UUID customerId,
            @Param("softwareId") UUID softwareId,
            @Param("templateId") UUID templateId,
            @Param("status") com.secvulmanager.api.model.Enums.UploadStatus status,
            @Param("activeSnapshot") Boolean activeSnapshot
    );

    @Query(value = """
            SELECT u FROM UploadDetails u
            WHERE (:customerId IS NULL OR u.customer.id = :customerId)
              AND (:softwareId IS NULL OR u.software.id = :softwareId)
              AND (:templateId IS NULL OR u.template.id = :templateId)
              AND (:status IS NULL OR u.status = :status)
              AND (:activeSnapshot IS NULL OR u.isActiveSnapshot = :activeSnapshot)
            ORDER BY u.uploadedAt DESC
            """,
            countQuery = """
            SELECT COUNT(u) FROM UploadDetails u
            WHERE (:customerId IS NULL OR u.customer.id = :customerId)
              AND (:softwareId IS NULL OR u.software.id = :softwareId)
              AND (:templateId IS NULL OR u.template.id = :templateId)
              AND (:status IS NULL OR u.status = :status)
              AND (:activeSnapshot IS NULL OR u.isActiveSnapshot = :activeSnapshot)
            """)
    Page<UploadDetails> findHistory(
            @Param("customerId") UUID customerId,
            @Param("softwareId") UUID softwareId,
            @Param("templateId") UUID templateId,
            @Param("status") com.secvulmanager.api.model.Enums.UploadStatus status,
            @Param("activeSnapshot") Boolean activeSnapshot,
            Pageable pageable
    );

    @Query("""
            SELECT u FROM UploadDetails u
            WHERE u.customer.id IN :customerIds
              AND (:softwareId IS NULL OR u.software.id = :softwareId)
              AND (:templateId IS NULL OR u.template.id = :templateId)
              AND (:status IS NULL OR u.status = :status)
              AND (:activeSnapshot IS NULL OR u.isActiveSnapshot = :activeSnapshot)
            ORDER BY u.uploadedAt DESC
            """)
    List<UploadDetails> findHistoryForCustomers(
            @Param("customerIds") List<UUID> customerIds,
            @Param("softwareId") UUID softwareId,
            @Param("templateId") UUID templateId,
            @Param("status") com.secvulmanager.api.model.Enums.UploadStatus status,
            @Param("activeSnapshot") Boolean activeSnapshot
    );

    @Query(value = """
            SELECT u FROM UploadDetails u
            WHERE u.customer.id IN :customerIds
              AND (:softwareId IS NULL OR u.software.id = :softwareId)
              AND (:templateId IS NULL OR u.template.id = :templateId)
              AND (:status IS NULL OR u.status = :status)
              AND (:activeSnapshot IS NULL OR u.isActiveSnapshot = :activeSnapshot)
            ORDER BY u.uploadedAt DESC
            """,
            countQuery = """
            SELECT COUNT(u) FROM UploadDetails u
            WHERE u.customer.id IN :customerIds
              AND (:softwareId IS NULL OR u.software.id = :softwareId)
              AND (:templateId IS NULL OR u.template.id = :templateId)
              AND (:status IS NULL OR u.status = :status)
              AND (:activeSnapshot IS NULL OR u.isActiveSnapshot = :activeSnapshot)
            """)
    Page<UploadDetails> findHistoryForCustomers(
            @Param("customerIds") List<UUID> customerIds,
            @Param("softwareId") UUID softwareId,
            @Param("templateId") UUID templateId,
            @Param("status") com.secvulmanager.api.model.Enums.UploadStatus status,
            @Param("activeSnapshot") Boolean activeSnapshot,
            Pageable pageable
    );
}
