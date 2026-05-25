package com.secvulmanager.api.repository;

import com.secvulmanager.api.model.CustomerSoftwareAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerSoftwareAccessRepository extends JpaRepository<CustomerSoftwareAccess, UUID> {
    List<CustomerSoftwareAccess> findByCustomerId(UUID customerId);
    Optional<CustomerSoftwareAccess> findByCustomerIdAndSoftwareId(UUID customerId, UUID softwareId);
    boolean existsByCustomerIdAndSoftwareIdAndEnabledTrue(UUID customerId, UUID softwareId);

    @Transactional
    void deleteByCustomerId(UUID customerId);
}
