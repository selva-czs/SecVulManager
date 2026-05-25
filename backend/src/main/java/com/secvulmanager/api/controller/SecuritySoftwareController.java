package com.secvulmanager.api.controller;

import com.secvulmanager.api.model.AppUser;
import com.secvulmanager.api.model.SecuritySoftware;
import com.secvulmanager.api.repository.AppUserRepository;
import com.secvulmanager.api.repository.SecuritySoftwareRepository;
import com.secvulmanager.api.service.AuthorizationUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/software")
public class SecuritySoftwareController {

    private final SecuritySoftwareRepository softwareRepository;
    private final AppUserRepository userRepository;
    private final AuthorizationUtil authUtil;

    public SecuritySoftwareController(SecuritySoftwareRepository softwareRepository,
                                      AppUserRepository userRepository,
                                      AuthorizationUtil authUtil) {
        this.softwareRepository = softwareRepository;
        this.userRepository = userRepository;
        this.authUtil = authUtil;
    }

    private AppUser getCurrentUser() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(name).orElse(null);
    }

    @GetMapping
    public ResponseEntity<?> getAllSoftware() {
        return ResponseEntity.ok(softwareRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> createSoftware(@RequestBody Map<String, String> request) {
        AppUser currentUser = getCurrentUser();
        if (!authUtil.isSuperAdmin(currentUser) && !authUtil.isSecurityOperator(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only administrators or security operators can manage the Software Registry\"}");
        }

        String name = request.get("softwareName");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("{\"error\": \"softwareName is required\"}");
        }

        if (softwareRepository.findBySoftwareName(name.trim()).isPresent()) {
            return ResponseEntity.badRequest().body("{\"error\": \"Software registry item already exists\"}");
        }

        SecuritySoftware sw = new SecuritySoftware(name.trim(), true);
        sw = softwareRepository.save(sw);
        return ResponseEntity.status(HttpStatus.CREATED).body(sw);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSoftware(@PathVariable UUID id, @RequestBody Map<String, Object> request) {
        AppUser currentUser = getCurrentUser();
        if (!authUtil.isSuperAdmin(currentUser) && !authUtil.isSecurityOperator(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only administrators or security operators can manage the Software Registry\"}");
        }

        SecuritySoftware sw = softwareRepository.findById(id).orElse(null);
        if (sw == null) {
            return ResponseEntity.notFound().build();
        }

        Object nameObj = request.get("softwareName");
        if (nameObj instanceof String name && !name.trim().isEmpty()) {
            Optional<SecuritySoftware> duplicate = softwareRepository.findBySoftwareName(name.trim());
            if (duplicate.isPresent() && !duplicate.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body("{\"error\": \"Software registry item already exists\"}");
            }
            sw.setSoftwareName(name.trim());
        }

        Object enabledObj = request.get("enabled");
        if (enabledObj instanceof Boolean enabled) {
            sw.setEnabled(enabled);
        }
        Object archivedObj = request.get("archived");
        if (archivedObj instanceof Boolean archived) {
            sw.setArchived(archived);
            sw.setEnabled(false);
            sw.setArchivedAt(archived ? OffsetDateTime.now() : null);
            sw.setArchivedBy(archived ? currentUser.getUsername() : null);
        }

        return ResponseEntity.ok(softwareRepository.save(sw));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSoftware(@PathVariable UUID id) {
        AppUser currentUser = getCurrentUser();
        if (!authUtil.isSuperAdmin(currentUser) && !authUtil.isSecurityOperator(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Only administrators or security operators can manage the Software Registry\"}");
        }

        SecuritySoftware sw = softwareRepository.findById(id).orElse(null);
        if (sw == null) {
            return ResponseEntity.notFound().build();
        }

        sw.setArchived(true);
        sw.setEnabled(false);
        sw.setArchivedAt(OffsetDateTime.now());
        sw.setArchivedBy(currentUser.getUsername());
        return ResponseEntity.ok(softwareRepository.save(sw));
    }
}
