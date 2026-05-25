package com.secvulmanager.api.controller;

import com.secvulmanager.api.model.*;
import com.secvulmanager.api.repository.AppUserRepository;
import com.secvulmanager.api.repository.CustomerRepository;
import com.secvulmanager.api.repository.CustomerSoftwareAccessRepository;
import com.secvulmanager.api.repository.CustomerTemplateRepository;
import com.secvulmanager.api.repository.SecuritySoftwareRepository;
import com.secvulmanager.api.repository.UserCustomerAccessRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final AppUserRepository userRepository;
    private final UserCustomerAccessRepository customerAccessRepository;
    private final SecuritySoftwareRepository softwareRepository;
    private final CustomerSoftwareAccessRepository customerSoftwareAccessRepository;
    private final CustomerTemplateRepository templateRepository;

    public CustomerController(CustomerRepository customerRepository,
                              AppUserRepository userRepository,
                              UserCustomerAccessRepository customerAccessRepository,
                              SecuritySoftwareRepository softwareRepository,
                              CustomerSoftwareAccessRepository customerSoftwareAccessRepository,
                              CustomerTemplateRepository templateRepository) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.customerAccessRepository = customerAccessRepository;
        this.softwareRepository = softwareRepository;
        this.customerSoftwareAccessRepository = customerSoftwareAccessRepository;
        this.templateRepository = templateRepository;
    }

    private AppUser getCurrentUser() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(name).orElse(null);
    }

    private boolean canAccessCustomer(UUID customerId) {
        AppUser current = getCurrentUser();
        if (current == null) return false;
        if (current.getRole() == Enums.UserRole.SUPER_ADMIN || current.getRole() == Enums.UserRole.GLOBAL_OPERATOR) {
            return true;
        }
        return customerAccessRepository.existsByUserIdAndCustomerId(current.getId(), customerId);
    }

    @GetMapping
    public ResponseEntity<?> getAvailableCustomers() {
        AppUser current = getCurrentUser();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Customer> customers;
        if (current.getRole() == Enums.UserRole.SUPER_ADMIN || current.getRole() == Enums.UserRole.GLOBAL_OPERATOR) {
            customers = customerRepository.findAll();
        } else {
            // Return only mapped customers for tenant operators
            customers = customerAccessRepository.findByUserId(current.getId()).stream()
                    .map(UserCustomerAccess::getCustomer)
                    .collect(Collectors.toList());
        }

        enrichCustomerSummaries(customers);
        return ResponseEntity.ok(customers);
    }

    private void enrichCustomerSummaries(List<Customer> customers) {
        if (customers.isEmpty()) return;

        Set<UUID> customerIds = customers.stream()
                .map(Customer::getId)
                .collect(Collectors.toSet());

        Map<UUID, List<CustomerSoftwareAccess>> accessByCustomer = customerSoftwareAccessRepository.findAll().stream()
                .filter(access -> access.getCustomer() != null && customerIds.contains(access.getCustomer().getId()))
                .filter(access -> access.getSoftware() != null && !access.getSoftware().isArchived())
                .collect(Collectors.groupingBy(access -> access.getCustomer().getId()));

        List<CustomerTemplate> activeTemplates = templateRepository.findAll().stream()
                .filter(template -> !template.isArchived() && template.isEnabled() && template.getSoftware() != null)
                .toList();

        for (Customer customer : customers) {
            List<CustomerSoftwareAccess> assignments = accessByCustomer.getOrDefault(customer.getId(), List.of());
            Set<UUID> assignedSoftwareIds = assignments.stream()
                    .map(CustomerSoftwareAccess::getSoftware)
                    .filter(Objects::nonNull)
                    .map(SecuritySoftware::getId)
                    .collect(Collectors.toSet());

            long activeTemplateCount = activeTemplates.stream()
                    .filter(template -> assignedSoftwareIds.contains(template.getSoftware().getId()))
                    .filter(template -> template.getCustomer() == null || template.getCustomer().getId().equals(customer.getId()))
                    .count();

            customer.setAssignedSoftwareCount(assignments.size());
            customer.setEnabledAssignedSoftwareCount(assignments.stream().filter(CustomerSoftwareAccess::isEnabled).count());
            customer.setActiveTemplateCount(activeTemplateCount);
        }
    }

    @PostMapping
    public ResponseEntity<?> createCustomer(@RequestBody Map<String, String> request) {
        AppUser current = getCurrentUser();
        if (current == null || current.getRole() != Enums.UserRole.SUPER_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only Super Admins can register customers\"}");
        }

        String name = request.get("customerName");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("{\"error\": \"customerName is required\"}");
        }

        if (customerRepository.findByCustomerName(name.trim()).isPresent()) {
            return ResponseEntity.badRequest().body("{\"error\": \"Customer name already exists\"}");
        }

        Customer c = new Customer(name.trim(), current.getUsername());
        c = customerRepository.save(c);
        return ResponseEntity.status(HttpStatus.CREATED).body(c);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCustomerById(@PathVariable UUID id) {
        if (!canAccessCustomer(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Access denied to customer profile\"}");
        }

        return customerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateCustomer(@PathVariable UUID id, @RequestBody Map<String, Object> request) {
        AppUser current = getCurrentUser();
        if (current == null || current.getRole() != Enums.UserRole.SUPER_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only Super Admins can update customers\"}");
        }

        Customer customer = customerRepository.findById(id).orElse(null);
        if (customer == null) {
            return ResponseEntity.notFound().build();
        }

        Object nameObj = request.get("customerName");
        if (nameObj instanceof String name) {
            if (name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\": \"customerName cannot be empty\"}");
            }

            Optional<Customer> duplicate = customerRepository.findByCustomerName(name.trim());
            if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body("{\"error\": \"Customer name already exists\"}");
            }
            customer.setCustomerName(name.trim());
        }

        Object enabledObj = request.get("enabled");
        if (enabledObj instanceof Boolean enabled) {
            customer.setEnabled(enabled);
        }
        Object archivedObj = request.get("archived");
        if (archivedObj instanceof Boolean archived) {
            customer.setArchived(archived);
            customer.setEnabled(false);
            customer.setArchivedAt(archived ? OffsetDateTime.now() : null);
            customer.setArchivedBy(archived ? current.getUsername() : null);
        }

        customer.setUpdatedBy(current.getUsername());
        return ResponseEntity.ok(customerRepository.save(customer));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCustomer(@PathVariable UUID id) {
        AppUser current = getCurrentUser();
        if (current == null || current.getRole() != Enums.UserRole.SUPER_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only Super Admins can delete customers\"}");
        }

        Customer customer = customerRepository.findById(id).orElse(null);
        if (customer == null) {
            return ResponseEntity.notFound().build();
        }

        customer.setArchived(true);
        customer.setEnabled(false);
        customer.setArchivedAt(OffsetDateTime.now());
        customer.setArchivedBy(current.getUsername());
        customer.setUpdatedBy(current.getUsername());
        return ResponseEntity.ok(customerRepository.save(customer));
    }

    @GetMapping("/{id}/software-access")
    public ResponseEntity<?> getCustomerSoftwareAccess(@PathVariable UUID id) {
        AppUser current = getCurrentUser();
        if (current == null || !canAccessCustomer(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Access denied to customer software assignments\"}");
        }

        Customer customer = customerRepository.findById(id).orElse(null);
        if (customer == null) {
            return ResponseEntity.notFound().build();
        }

        Map<UUID, CustomerSoftwareAccess> accessBySoftware = customerSoftwareAccessRepository.findByCustomerId(id).stream()
                .collect(Collectors.toMap(access -> access.getSoftware().getId(), access -> access));

        List<Map<String, Object>> rows = softwareRepository.findAll().stream()
                .filter(software -> !software.isArchived())
                .map(software -> {
                    CustomerSoftwareAccess access = accessBySoftware.get(software.getId());
                    long activeTemplateCount = templateRepository.findBySoftwareId(software.getId()).stream()
                            .filter(template -> !template.isArchived() && template.isEnabled())
                            .filter(template -> template.getCustomer() == null || template.getCustomer().getId().equals(id))
                            .count();
                    Map<String, Object> row = new HashMap<>();
                    row.put("software", software);
                    row.put("assigned", access != null);
                    row.put("enabled", access != null && access.isEnabled());
                    row.put("activeTemplateCount", activeTemplateCount);
                    return row;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "customerId", id,
            "assignments", rows
        ));
    }

    @PutMapping("/{id}/software-access")
    @Transactional
    public ResponseEntity<?> updateCustomerSoftwareAccess(@PathVariable UUID id, @RequestBody Map<String, Object> request) {
        AppUser current = getCurrentUser();
        if (current == null || current.getRole() != Enums.UserRole.SUPER_ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only Super Admins can manage customer software assignments\"}");
        }

        Customer customer = customerRepository.findById(id).orElse(null);
        if (customer == null) {
            return ResponseEntity.notFound().build();
        }

        Object rawAssignments = request.get("assignments");
        if (!(rawAssignments instanceof List<?> assignmentList)) {
            return ResponseEntity.badRequest().body("{\"error\": \"assignments list is required\"}");
        }

        customerSoftwareAccessRepository.deleteByCustomerId(id);
        for (Object item : assignmentList) {
            if (!(item instanceof Map<?, ?> assignment)) continue;
            Object softwareIdValue = assignment.get("softwareId");
            if (softwareIdValue == null) continue;
            UUID softwareId = UUID.fromString(String.valueOf(softwareIdValue));
            SecuritySoftware software = softwareRepository.findById(softwareId).orElse(null);
            if (software == null || software.isArchived()) continue;
            Object enabledValue = assignment.get("enabled");
            boolean enabled = enabledValue == null || Boolean.parseBoolean(String.valueOf(enabledValue));
            customerSoftwareAccessRepository.save(new CustomerSoftwareAccess(customer, software, enabled));
        }

        return getCustomerSoftwareAccess(id);
    }
}
