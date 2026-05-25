package com.secvulmanager.api.controller;

import com.secvulmanager.api.model.*;
import com.secvulmanager.api.repository.AppUserRepository;
import com.secvulmanager.api.repository.CustomerRepository;
import com.secvulmanager.api.repository.UserCustomerAccessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AppUserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final UserCustomerAccessRepository customerAccessRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(AppUserRepository userRepository,
                          CustomerRepository customerRepository,
                          UserCustomerAccessRepository customerAccessRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.customerAccessRepository = customerAccessRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private boolean isSuperAdmin() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(name)
                .map(u -> u.getRole() == Enums.UserRole.SUPER_ADMIN)
                .orElse(false);
    }

    private Map<String, Object> buildUserResponse(AppUser user) {
        Map<String, Object> uMap = new HashMap<>();
        uMap.put("id", user.getId());
        uMap.put("username", user.getUsername());
        uMap.put("fullName", user.getFullName());
        uMap.put("role", user.getRole().name());
        uMap.put("enabled", user.isEnabled());
        uMap.put("archived", user.isArchived());
        uMap.put("archivedAt", user.getArchivedAt());
        uMap.put("archivedBy", user.getArchivedBy());
        uMap.put("createdAt", user.getCreatedAt());

        List<Map<String, Object>> allowedCustomers = customerAccessRepository.findByUserId(user.getId()).stream()
                .map(access -> {
                    Map<String, Object> cMap = new HashMap<>();
                    cMap.put("id", access.getCustomer().getId());
                    cMap.put("customerName", access.getCustomer().getCustomerName());
                    return cMap;
                })
                .collect(Collectors.toList());
        uMap.put("allowedCustomers", allowedCustomers);
        return uMap;
    }

    @GetMapping
    public ResponseEntity<?> getAllUsers() {
        if (!isSuperAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only Super Admins can manage users\"}");
        }

        List<AppUser> users = userRepository.findAll();
        List<Map<String, Object>> response = users.stream()
                .map(this::buildUserResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> request) {
        if (!isSuperAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only Super Admins can onboard users\"}");
        }

        String username = (String) request.get("username");
        String password = (String) request.get("password");
        String fullName = (String) request.get("fullName");
        String roleStr = (String) request.get("role");

        if (username == null || password == null || fullName == null || roleStr == null) {
            return ResponseEntity.badRequest().body("{\"error\": \"All fields (username, password, fullName, role) are required\"}");
        }

        if (userRepository.findByUsername(username.trim()).isPresent()) {
            return ResponseEntity.badRequest().body("{\"error\": \"Username already exists\"}");
        }

        Enums.UserRole role;
        try {
            role = Enums.UserRole.valueOf(roleStr.trim().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"error\": \"Invalid role type. Must be SUPER_ADMIN, GLOBAL_OPERATOR, or CUSTOMER_OPERATOR\"}");
        }

        AppUser newUser = new AppUser(username.trim(), passwordEncoder.encode(password), fullName.trim(), role);
        newUser = userRepository.save(newUser);

        // Map allowed customer list if provided and it's a CUSTOMER_OPERATOR
        List<String> customerIds = (List<String>) request.get("allowedCustomerIds");
        if (customerIds != null && role == Enums.UserRole.CUSTOMER_OPERATOR) {
            for (String cid : customerIds) {
                UUID customerUuid = UUID.fromString(cid);
                Customer c = customerRepository.findById(customerUuid).orElse(null);
                if (c != null) {
                    UserCustomerAccess access = new UserCustomerAccess(newUser, c);
                    customerAccessRepository.save(access);
                }
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(buildUserResponse(newUser));
    }

    @PutMapping("/{id}/access")
    @Transactional
    public ResponseEntity<?> updateUserAccess(@PathVariable UUID id, @RequestBody Map<String, List<String>> request) {
        if (!isSuperAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only Super Admins can manage access controls\"}");
        }

        AppUser user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        List<String> customerIds = request.get("allowedCustomerIds");
        if (customerIds == null) {
            return ResponseEntity.badRequest().body("{\"error\": \"allowedCustomerIds list is required\"}");
        }

        // Delete all old access mappings
        customerAccessRepository.deleteByUserId(user.getId());

        // Map new accesses
        if (user.getRole() == Enums.UserRole.CUSTOMER_OPERATOR) {
            for (String cid : customerIds) {
                UUID customerUuid = UUID.fromString(cid);
                Customer c = customerRepository.findById(customerUuid).orElse(null);
                if (c != null) {
                    UserCustomerAccess access = new UserCustomerAccess(user, c);
                    customerAccessRepository.save(access);
                }
            }
        }

        return ResponseEntity.ok("{\"message\": \"User access rights successfully updated\"}");
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateUserStatus(@PathVariable UUID id, @RequestBody Map<String, Boolean> request) {
        if (!isSuperAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only Super Admins can toggle user state\"}");
        }

        AppUser user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        Boolean enabled = request.get("enabled");
        if (enabled == null) {
            return ResponseEntity.badRequest().body("{\"error\": \"enabled parameter is required\"}");
        }

        // Prevent disabling yourself
        String currentName = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.getUsername().equals(currentName) && !enabled) {
            return ResponseEntity.badRequest().body("{\"error\": \"You cannot disable your own enabled session\"}");
        }

        user.setEnabled(enabled);
        user = userRepository.save(user);
        return ResponseEntity.ok(buildUserResponse(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> archiveUser(@PathVariable UUID id) {
        if (!isSuperAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only Super Admins can archive users\"}");
        }

        AppUser user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        String currentName = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.getUsername().equals(currentName)) {
            return ResponseEntity.badRequest().body("{\"error\": \"You cannot archive your own enabled session\"}");
        }

        user.setArchived(true);
        user.setEnabled(false);
        user.setArchivedAt(OffsetDateTime.now());
        user.setArchivedBy(currentName);
        user = userRepository.save(user);
        return ResponseEntity.ok(buildUserResponse(user));
    }

    @PutMapping("/{id}/restore")
    public ResponseEntity<?> restoreUser(@PathVariable UUID id) {
        if (!isSuperAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only Super Admins can restore users\"}");
        }

        AppUser user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        user.setArchived(false);
        user.setEnabled(false);
        user.setArchivedAt(null);
        user.setArchivedBy(null);
        user = userRepository.save(user);
        return ResponseEntity.ok(buildUserResponse(user));
    }
}
