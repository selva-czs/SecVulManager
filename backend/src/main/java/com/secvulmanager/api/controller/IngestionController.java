package com.secvulmanager.api.controller;

import com.secvulmanager.api.model.*;
import com.secvulmanager.api.repository.AppUserRepository;
import com.secvulmanager.api.repository.UploadDetailsRepository;
import com.secvulmanager.api.repository.UserCustomerAccessRepository;
import com.secvulmanager.api.service.ETLService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
            @RequestParam("templateId") UUID templateId,
            @RequestParam(value = "queueMode", defaultValue = "REJECT_IF_BUSY") String queueMode,
            @RequestParam(value = "queueComment", required = false) String queueComment) {
        
        AppUser current = getCurrentUser();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!canAccessCustomer(customerId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("{\"error\": \"Access denied: You are not authorized to upload data for this customer\"}");
        }

        try {
            Enums.QueueMode mode = Enums.QueueMode.valueOf(queueMode.trim().toUpperCase(Locale.ROOT));
            ETLService.IngestionSubmission submission = etlService.submitUpload(file, customerId, templateId, current.getUsername(), mode, queueComment);
            if (submission.busy()) {
                UploadDetails running = submission.runningUpload();
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "An upload is already processing for this customer. Wait for it to finish or queue this upload.",
                        "busy", true,
                        "canQueue", true,
                        "runningUploadId", running.getId(),
                        "runningFileName", running.getFileName(),
                        "runningUploadedBy", running.getUploadedBy(),
                        "runningStartedAt", running.getStartedAt() != null ? running.getStartedAt() : running.getUploadedAt()
                ));
            }
            return ResponseEntity.ok(submission.upload());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ingestion engine failed with an internal system error: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getIngestionHistory(@RequestParam(required = false) UUID customerId,
                                                 @RequestParam(required = false) UUID softwareId,
                                                 @RequestParam(required = false) UUID templateId,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) Boolean activeSnapshot,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        AppUser current = getCurrentUser();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean paged = page != null || size != null;
        Pageable pageable = paged ? toPageable(page, size) : Pageable.unpaged();
        List<UploadDetails> history;
        Page<UploadDetails> historyPage;
        Enums.UploadStatus uploadStatus = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            uploadStatus = Enums.UploadStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        }

        if (customerId != null) {
            if (!canAccessCustomer(customerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Access denied\"}");
            }
            if (paged) {
                historyPage = uploadRepository.findHistory(customerId, softwareId, templateId, uploadStatus, activeSnapshot, pageable);
                return ResponseEntity.ok(historyPage);
            }
            history = uploadRepository.findHistory(customerId, softwareId, templateId, uploadStatus, activeSnapshot);
        } else {
            // Global or operator scope consolidation
            if (current.getRole() == Enums.UserRole.SUPER_ADMIN || current.getRole() == Enums.UserRole.GLOBAL_OPERATOR) {
                if (paged) {
                    historyPage = uploadRepository.findHistory(null, softwareId, templateId, uploadStatus, activeSnapshot, pageable);
                    return ResponseEntity.ok(historyPage);
                }
                history = uploadRepository.findHistory(null, softwareId, templateId, uploadStatus, activeSnapshot);
            } else {
                List<UUID> allowedCustomerIds = customerAccessRepository.findByUserId(current.getId()).stream()
                        .map(access -> access.getCustomer().getId())
                        .collect(Collectors.toList());

                if (allowedCustomerIds.isEmpty()) {
                    return ResponseEntity.ok(paged ? Page.empty(pageable) : List.of());
                }
                if (paged) {
                    historyPage = uploadRepository.findHistoryForCustomers(allowedCustomerIds, softwareId, templateId, uploadStatus, activeSnapshot, pageable);
                    return ResponseEntity.ok(historyPage);
                }
                history = uploadRepository.findHistoryForCustomers(allowedCustomerIds, softwareId, templateId, uploadStatus, activeSnapshot);
            }
        }

        return ResponseEntity.ok(history);
    }

    private Pageable toPageable(Integer page, Integer size) {
        int pageNumber = page != null ? page : 0;
        int pageSize = size != null ? size : 50;
        if (pageNumber < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be greater than or equal to 0");
        }
        if (pageSize < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be greater than 0");
        }
        return PageRequest.of(pageNumber, pageSize);
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

    @PostMapping("/{id}/activate")
    public ResponseEntity<?> activateUploadSnapshot(@PathVariable UUID id) {
        AppUser current = getCurrentUser();
        if (current == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UploadDetails run = uploadRepository.findById(id).orElse(null);
        if (run == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canAccessCustomer(run.getCustomer().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("{\"error\": \"Access denied to activate upload history\"}");
        }

        try {
            return ResponseEntity.ok(etlService.activateUploadSnapshot(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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

        String uploadPath = run.getUploadedFilePath() != null ? run.getUploadedFilePath() : run.getSampleFilePath();
        if (uploadPath == null) {
            return ResponseEntity.badRequest().body("{\"error\": \"No sample file available for this upload run\"}");
        }

        File file = new File(uploadPath);
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
