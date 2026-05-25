package com.secvulmanager.api.repository;

import com.secvulmanager.api.model.UploadDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UploadDetailsRepository extends JpaRepository<UploadDetails, UUID> {
    List<UploadDetails> findByCustomerIdOrderByUploadedAtDesc(UUID customerId);

    @Query("SELECT u FROM UploadDetails u WHERE u.customer.id = :customerId AND u.isActiveSnapshot = true")
    Optional<UploadDetails> findActiveSnapshotForCustomer(@Param("customerId") UUID customerId);

    @Query("SELECT u FROM UploadDetails u WHERE u.customer.id = :customerId AND u.software.id = :softwareId AND u.isActiveSnapshot = true")
    Optional<UploadDetails> findActiveSnapshotForCustomerAndSoftware(@Param("customerId") UUID customerId, @Param("softwareId") UUID softwareId);

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
}
