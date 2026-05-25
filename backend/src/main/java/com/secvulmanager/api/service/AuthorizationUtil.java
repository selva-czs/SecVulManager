package com.secvulmanager.api.service;

import com.secvulmanager.api.model.AppUser;
import com.secvulmanager.api.model.Enums;
import com.secvulmanager.api.repository.UserCustomerAccessRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthorizationUtil {

    private final UserCustomerAccessRepository userCustomerAccessRepository;

    public AuthorizationUtil(UserCustomerAccessRepository userCustomerAccessRepository) {
        this.userCustomerAccessRepository = userCustomerAccessRepository;
    }

    public boolean isSuperAdmin(AppUser user) {
        return user != null && user.getRole() == Enums.UserRole.SUPER_ADMIN;
    }

    public boolean isSecurityOperator(AppUser user) {
        return user != null && user.getRole() == Enums.UserRole.SECURITY_OPERATOR;
    }

    public boolean isSuperAdminOrGlobalOperator(AppUser user) {
        return user != null && (user.getRole() == Enums.UserRole.SUPER_ADMIN || user.getRole() == Enums.UserRole.GLOBAL_OPERATOR);
    }

    public boolean canAccessCustomer(AppUser user, UUID customerId) {
        if (user == null) return false;
        if (isSuperAdminOrGlobalOperator(user)) return true;
        
        return userCustomerAccessRepository.findByUserId(user.getId()).stream()
                .anyMatch(access -> access.getCustomer().getId().equals(customerId));
    }

    public boolean canManageTemplates(AppUser user, UUID customerId) {
        if (user == null) return false;
        if (isSuperAdmin(user)) return true;
        if (isSecurityOperator(user)) {
            return canAccessCustomer(user, customerId);
        }
        return false;
    }
}
