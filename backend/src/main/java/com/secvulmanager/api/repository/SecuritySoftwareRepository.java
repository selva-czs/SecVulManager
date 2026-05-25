package com.secvulmanager.api.repository;

import com.secvulmanager.api.model.SecuritySoftware;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecuritySoftwareRepository extends JpaRepository<SecuritySoftware, UUID> {
    Optional<SecuritySoftware> findBySoftwareName(String softwareName);
}
