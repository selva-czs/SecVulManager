package com.secvulmanager.api.repository;

import com.secvulmanager.api.model.CustomerTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerTemplateRepository extends JpaRepository<CustomerTemplate, UUID> {
    List<CustomerTemplate> findByCustomerId(UUID customerId);
    List<CustomerTemplate> findBySoftwareId(UUID softwareId);
    List<CustomerTemplate> findByCustomerIdAndSoftwareId(UUID customerId, UUID softwareId);
    List<CustomerTemplate> findByCustomerIsNull();
}
