package com.secvulmanager.api.repository;

import com.secvulmanager.api.model.UserSavedView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserSavedViewRepository {
    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UserSavedView> rowMapper = this::mapRow;

    public UserSavedViewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<UserSavedView> findByUserIdAndViewType(UUID userId, String viewType) {
        return jdbcTemplate.query("""
            SELECT id, user_id, name, view_type, is_default, filters_json::text, sort_json::text, created_at, updated_at
            FROM user_saved_view
            WHERE user_id = ? AND view_type = ?
            ORDER BY is_default DESC, name ASC
            """, rowMapper, userId, viewType);
    }

    public Optional<UserSavedView> findByIdAndUserId(UUID id, UUID userId) {
        List<UserSavedView> views = jdbcTemplate.query("""
            SELECT id, user_id, name, view_type, is_default, filters_json::text, sort_json::text, created_at, updated_at
            FROM user_saved_view
            WHERE id = ? AND user_id = ?
            """, rowMapper, id, userId);
        return views.stream().findFirst();
    }

    public UserSavedView create(UUID userId, String name, String viewType, boolean defaultView, String filtersJson, String sortJson) {
        if (defaultView) clearDefault(userId, viewType);
        return jdbcTemplate.queryForObject("""
            INSERT INTO user_saved_view (user_id, name, view_type, is_default, filters_json, sort_json)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)
            RETURNING id, user_id, name, view_type, is_default, filters_json::text, sort_json::text, created_at, updated_at
            """, rowMapper, userId, name, viewType, defaultView, filtersJson, sortJson);
    }

    public Optional<UserSavedView> update(UUID id, UUID userId, String name, boolean defaultView, String filtersJson, String sortJson) {
        Optional<UserSavedView> existing = findByIdAndUserId(id, userId);
        if (existing.isEmpty()) return Optional.empty();
        if (defaultView) clearDefault(userId, existing.get().getViewType());
        List<UserSavedView> views = jdbcTemplate.query("""
            UPDATE user_saved_view
            SET name = ?, is_default = ?, filters_json = ?::jsonb, sort_json = ?::jsonb, updated_at = now()
            WHERE id = ? AND user_id = ?
            RETURNING id, user_id, name, view_type, is_default, filters_json::text, sort_json::text, created_at, updated_at
            """, rowMapper, name, defaultView, filtersJson, sortJson, id, userId);
        return views.stream().findFirst();
    }

    public boolean delete(UUID id, UUID userId) {
        return jdbcTemplate.update("DELETE FROM user_saved_view WHERE id = ? AND user_id = ?", id, userId) > 0;
    }

    public Optional<UserSavedView> setDefault(UUID id, UUID userId) {
        Optional<UserSavedView> existing = findByIdAndUserId(id, userId);
        if (existing.isEmpty()) return Optional.empty();
        clearDefault(userId, existing.get().getViewType());
        jdbcTemplate.update("UPDATE user_saved_view SET is_default = true, updated_at = now() WHERE id = ? AND user_id = ?", id, userId);
        return findByIdAndUserId(id, userId);
    }

    private void clearDefault(UUID userId, String viewType) {
        jdbcTemplate.update("UPDATE user_saved_view SET is_default = false, updated_at = now() WHERE user_id = ? AND view_type = ?", userId, viewType);
    }

    private UserSavedView mapRow(ResultSet rs, int rowNum) throws SQLException {
        UserSavedView view = new UserSavedView();
        view.setId((UUID) rs.getObject("id"));
        view.setUserId((UUID) rs.getObject("user_id"));
        view.setName(rs.getString("name"));
        view.setViewType(rs.getString("view_type"));
        view.setDefaultView(rs.getBoolean("is_default"));
        view.setFiltersJson(rs.getString("filters_json"));
        view.setSortJson(rs.getString("sort_json"));
        view.setCreatedAt(rs.getObject("created_at", java.time.OffsetDateTime.class));
        view.setUpdatedAt(rs.getObject("updated_at", java.time.OffsetDateTime.class));
        return view;
    }
}
