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

    List<UploadDetails> findByUploadedByOrderByUploadedAtDesc(String uploadedBy);
}
