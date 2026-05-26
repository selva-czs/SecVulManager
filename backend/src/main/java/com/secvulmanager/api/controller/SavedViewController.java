package com.secvulmanager.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secvulmanager.api.model.AppUser;
import com.secvulmanager.api.model.UserSavedView;
import com.secvulmanager.api.repository.AppUserRepository;
import com.secvulmanager.api.repository.UserSavedViewRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me/saved-views")
public class SavedViewController {
    private static final String DEFAULT_VIEW_TYPE = "ACTIVE_FINDINGS";

    private final UserSavedViewRepository savedViewRepository;
    private final AppUserRepository userRepository;
    private final ObjectMapper objectMapper;

    public SavedViewController(UserSavedViewRepository savedViewRepository,
                               AppUserRepository userRepository,
                               ObjectMapper objectMapper) {
        this.savedViewRepository = savedViewRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(defaultValue = DEFAULT_VIEW_TYPE) String viewType) {
        AppUser current = getCurrentUser();
        if (current == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(savedViewRepository.findByUserIdAndViewType(current.getId(), normalizeViewType(viewType))
                .stream()
                .map(this::response)
                .toList());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> request) {
        AppUser current = getCurrentUser();
        if (current == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String name = cleanName(request.get("name"));
        if (name == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Saved view name is required"));
        }
        String viewType = normalizeViewType(asString(request.getOrDefault("viewType", DEFAULT_VIEW_TYPE)));
        boolean defaultView = Boolean.TRUE.equals(request.get("defaultView")) || Boolean.TRUE.equals(request.get("isDefault"));
        try {
            UserSavedView view = savedViewRepository.create(
                    current.getId(),
                    name,
                    viewType,
                    defaultView,
                    toJsonObject(request.get("filters")),
                    toJsonObject(request.get("sort"))
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(response(view));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "A saved view with this name already exists"));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Map<String, Object> request) {
        AppUser current = getCurrentUser();
        if (current == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String name = cleanName(request.get("name"));
        if (name == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Saved view name is required"));
        }
        boolean defaultView = Boolean.TRUE.equals(request.get("defaultView")) || Boolean.TRUE.equals(request.get("isDefault"));
        try {
            return savedViewRepository.update(
                            id,
                            current.getId(),
                            name,
                            defaultView,
                            toJsonObject(request.get("filters")),
                            toJsonObject(request.get("sort"))
                    )
                    .<ResponseEntity<?>>map((view) -> ResponseEntity.ok(response(view)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "A saved view with this name already exists"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable UUID id) {
        AppUser current = getCurrentUser();
        if (current == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!savedViewRepository.delete(id, current.getId())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/default")
    public ResponseEntity<?> setDefault(@PathVariable UUID id) {
        AppUser current = getCurrentUser();
        if (current == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return savedViewRepository.setDefault(id, current.getId())
                .<ResponseEntity<?>>map((view) -> ResponseEntity.ok(response(view)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private AppUser getCurrentUser() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(name).orElse(null);
    }

    private String cleanName(Object value) {
        String name = asString(value);
        if (name == null || name.trim().isEmpty()) return null;
        return name.trim();
    }

    private String normalizeViewType(String value) {
        String normalized = value == null || value.isBlank() ? DEFAULT_VIEW_TYPE : value.trim().toUpperCase();
        if (!normalized.matches("[A-Z0-9_]{1,60}")) {
            throw new IllegalArgumentException("Invalid viewType");
        }
        return normalized;
    }

    private String toJsonObject(Object value) {
        Object source = value instanceof Map<?, ?> ? value : Map.of();
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid saved view JSON", ex);
        }
    }

    private Map<String, Object> response(UserSavedView view) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", view.getId());
        response.put("name", view.getName());
        response.put("viewType", view.getViewType());
        response.put("defaultView", view.isDefaultView());
        response.put("filters", fromJson(view.getFiltersJson()));
        response.put("sort", fromJson(view.getSortJson()));
        response.put("createdAt", view.getCreatedAt());
        response.put("updatedAt", view.getUpdatedAt());
        return response;
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
