package com.secvulmanager.api.controller;

import com.secvulmanager.api.model.*;
import com.secvulmanager.api.repository.AppUserRepository;
import com.secvulmanager.api.repository.UploadDetailsRepository;
import com.secvulmanager.api.repository.UserCustomerAccessRepository;
import com.secvulmanager.api.service.ETLService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/uploads")
public class IngestionController {

    private final ETLService etlService;
    private final UploadDetailsRepository uploadRepository;
    private final AppUserRepository userRepository;
    private final UserCustomerAccessRepository customerAccessRepository;

    public IngestionController(ETLService etlService,
                               UploadDetailsRepository uploadRepository,
                               AppUserRepository userRepository,
                               UserCustomerAccessRepository customerAccessRepository) {
        this.etlService = etlService;
        this.uploadRepository = uploadRepository;
        this.userRepository = userRepository;
        this.customerAccessRepository = customerAccessRepository;
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

    @PostMapping("/ingest")
    public ResponseEntity<?> ingestFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("customerId") UUID customerId,
            @RequestParam("templateId") UUID templateId) {
        
        AppUser current = getCurrentUser();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!canAccessCustomer(customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\": \"Access denied: You are not authorized to upload data for this customer\"}");
        }

        try {
            UploadDetails runLog = etlService.ingestFile(file, customerId, templateId, current.getUsername());
            return ResponseEntity.ok(runLog);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Ingestion engine failed with an internal system error: " + e.getMessage() + "\"}");
        }
    }

    @GetMapping
    public ResponseEntity<?> getIngestionHistory(@RequestParam(required = false) UUID customerId) {
        AppUser current = getCurrentUser();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<UploadDetails> history;

        if (customerId != null) {
            if (!canAccessCustomer(customerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Access denied\"}");
            }
            history = uploadRepository.findByCustomerIdOrderByUploadedAtDesc(customerId);
        } else {
            // Global or operator scope consolidation
            if (current.getRole() == Enums.UserRole.SUPER_ADMIN || current.getRole() == Enums.UserRole.GLOBAL_OPERATOR) {
                history = uploadRepository.findAll().stream()
                        .sorted(Comparator.comparing(UploadDetails::getUploadedAt).reversed())
                        .collect(Collectors.toList());
            } else {
                // Fetch allowed customer list and consolidate history
                List<UUID> allowedCustomerIds = customerAccessRepository.findByUserId(current.getId()).stream()
                        .map(access -> access.getCustomer().getId())
                        .collect(Collectors.toList());
                
                history = uploadRepository.findAll().stream()
                        .filter(u -> allowedCustomerIds.contains(u.getCustomer().getId()))
                        .sorted(Comparator.comparing(UploadDetails::getUploadedAt).reversed())
                        .collect(Collectors.toList());
            }
        }

        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}/error-log")
    public ResponseEntity<?> downloadErrorLog(@PathVariable UUID id) {
        AppUser current = getCurrentUser();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UploadDetails run = uploadRepository.findById(id).orElse(null);
        if (run == null) {
            return ResponseEntity.notFound().build();
        }

        if (!canAccessCustomer(run.getCustomer().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Access denied to download file history\"}");
        }

        if (run.getErrorLogPath() == null) {
            return ResponseEntity.badRequest().body("{\"error\": \"No error log available for this upload run\"}");
        }

        File file = new File(run.getErrorLogPath());
        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Error log file was cleaned up or is unavailable on this server node\"}");
        }

        Resource resource = new FileSystemResource(file);
        boolean xlsx = file.getName().toLowerCase().endsWith(".xlsx");
        String baseName = run.getFileName() != null ? run.getFileName().replaceAll("\\.[^.]+$", "") : run.getId().toString();
        String downloadName = "failed_rows_" + baseName + (xlsx ? ".xlsx" : ".csv");
        MediaType contentType = xlsx
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv");

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/sample")
    public ResponseEntity<?> downloadOriginalSample(@PathVariable UUID id) {
        AppUser current = getCurrentUser();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UploadDetails run = uploadRepository.findById(id).orElse(null);
        if (run == null) {
            return ResponseEntity.notFound().build();
        }

        if (!canAccessCustomer(run.getCustomer().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Access denied to download file history\"}");
        }

        if (run.getSampleFilePath() == null) {
            return ResponseEntity.badRequest().body("{\"error\": \"No sample file available for this upload run\"}");
        }

        File file = new File(run.getSampleFilePath());
        if (!file.exists()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Sample file was cleaned up or is unavailable on this server node\"}");
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
