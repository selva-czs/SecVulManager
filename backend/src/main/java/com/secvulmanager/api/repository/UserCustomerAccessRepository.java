package com.secvulmanager.api.repository;

import com.secvulmanager.api.model.UserCustomerAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserCustomerAccessRepository extends JpaRepository<UserCustomerAccess, UUID> {
    List<UserCustomerAccess> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
    boolean existsByUserIdAndCustomerId(UUID userId, UUID customerId);
}
