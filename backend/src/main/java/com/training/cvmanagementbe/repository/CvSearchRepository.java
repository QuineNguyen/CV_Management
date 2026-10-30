package com.training.cvmanagementbe.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.training.cvmanagementbe.common.LikePatterns;
import com.training.cvmanagementbe.constant.CvContentKeys;
import com.training.cvmanagementbe.dto.response.cvs.CvSearchTeamTag;
import com.training.cvmanagementbe.entity.models.CvDraft;
import com.training.cvmanagementbe.enums.cvs.*;
import com.training.cvmanagementbe.record.cvs.CvSearchCriteria;
import com.training.cvmanagementbe.repository.projection.CvSearchRow;
import com.training.cvmanagementbe.repository.projection.DepartmentLink;
import com.training.cvmanagementbe.repository.projection.TeamLink;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/*
 * Native queries behind the CV search and the catalogue usage count.
 * - Plain SQL because the search needs MariaDB-only pieces: The latest-version subquery,
 * MATCH ... AGAINST on content_text and JSON functions over content_json.
 * - Clauses are built from constants and enums only; user input always travels as a parameter.
 * - Scope is a WHERE clause like any other filter - rows outside it are never loaded.
 */
@Repository
@RequiredArgsConstructor
public class CvSearchRepository {

    // InnoDB skips shorter tokens (innodb_ft_min_token_size = 3), so those fall back to LIKE
    private static final int FULLTEXT_MIN_TOKEN = 3;

    // A term the full-text parser reads as one word; others (C#, Node.js) go through LIKE
    private static final Pattern FULLTEXT_WORD = Pattern.compile("[\\p{L}\\p{N}\\p{M}]+");

    private static final String SKILL_ENTRIES_PATH = "$." + CvSectionKey.SKILLS.key() + "[*]";
    private static final String SKILL_IDS_PATH = SKILL_ENTRIES_PATH + "." + CvContentKeys.SKILL_ID;

    // lv = latest published version; UNIQUE(cv_id, version_number) serves the subquery
    private static final String FROM = """
            FROM cvs c
                         JOIN cv_profiles cp ON cp.id = c.profile_id
                         JOIN users u ON u.id = cp.employee_id
                         JOIN departments d ON d.id = u.primary_department_id
                         LEFT JOIN cv_versions lv ON lv.cv_id = c.id
                              AND lv.version_number = (SELECT MAX(v2.version_number) FROM cv_versions v2 WHERE v2.cv_id = c.id)
            """;

    private static final String SELECT = """
            SELECT c.id AS cv_id, c.language,
                   cp.name AS profile_name, cp.is_primary,
                   u.id AS employee_id, u.full_name, u.email, u.avatar_image_id,
                   d.code AS department_code, d.name AS department_name,
                   lv.version_number, lv.published_at,
                   JSON_EXTRACT(lv.content_json, '%s') AS skill_ids
            """.formatted(SKILL_IDS_PATH);

    // Keeps paging stable when the chosen sort has ties
    private static final List<String> TIE_BREAKERS =
            List.of("u.full_name ASC", "cp.name ASC", "c.language ASC", "c.id ASC");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    // ---------- CV search ----------

    public Page<CvSearchRow> search(CvSearchCriteria criteria, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = whereOf(criteria, params);

        Long total = jdbc.queryForObject("SELECT COUNT(*)" + FROM + where, params, Long.class);
        if (total == null || total == 0) {
            return Page.empty(pageable);
        }

        params.addValue("limit", pageable.getPageSize());
        params.addValue("offset", pageable.getOffset());

        List<CvSearchRow> rows = jdbc.query(
                SELECT + FROM + where + orderByOf(pageable.getSort()) + " LIMIT :limit OFFSET :offset",
                params,
                (rs, rowNum) -> toRow(rs)
        );
        return new PageImpl<>(rows, pageable, total);
    }

    // ---------- Lookups used next to the search ----------

    // Teams of each employee on a result page, primary team first; already the response shape
    public Map<UUID, List<CvSearchTeamTag>> teamsOf(Collection<UUID> userIds) {
        Map<UUID, List<CvSearchTeamTag>> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        String sql = """
                SELECT tm.user_id, t.code, t.name, tm.is_primary_team
                FROM team_members tm
                JOIN teams t ON t.id = tm.team_id
                WHERE tm.user_id IN (:userIds)
                ORDER BY tm.is_primary_team DESC, t.display_order ASC, t.name ASC
                """;
        RowCallbackHandler collect = rs -> result
                .computeIfAbsent(uuid(rs.getString("user_id")), key -> new ArrayList<>())
                .add(new CvSearchTeamTag(rs.getString("code"), rs.getString("name"), rs.getBoolean("is_primary_team")));

        jdbc.query(sql, new MapSqlParameterSource("userIds", strings(userIds)), collect);
        return result;
    }

    /*
     * cvId -> The content the untranslated counter reads: The open draft when there is one,
     * the latest published version otherwise. One query for the page; uk_cv_drafts_open keeps it to
     * one row per CV and only the chosen content leaves the database. A CV with neither is absent.
     */
    public Map<UUID, String> untranslatedSourcesOf(Collection<UUID> cvIds) {
        Map<UUID, String> result = new HashMap<>();
        if (cvIds.isEmpty()) {
            return result;
        }
        String sql = """
                SELECT c.id AS cv_id,
                                       COALESCE(od.content_json, lv.content_json) AS content_json
                                FROM cvs c
                                LEFT JOIN cv_drafts od ON od.cv_id = c.id AND od.status IN (:openStatuses)
                                LEFT JOIN cv_versions lv ON lv.cv_id = c.id
                                     AND lv.version_number = (SELECT MAX(v2.version_number) FROM cv_versions v2 WHERE v2.cv_id = c.id)
                                WHERE c.id IN (:cvIds)
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cvIds", strings(cvIds))
                .addValue("openStatuses", CvDraft.OPEN_STATUSES.stream().map(Enum::name).toList());

        jdbc.query(sql, params, (RowCallbackHandler) rs -> {
            String contentJson = rs.getString("content_json");
            if (contentJson != null) {
                result.putIfAbsent(uuid(rs.getString("cv_id")), contentJson);
            }
        });
        return result;
    }

    public List<DepartmentLink> departmentLinks() {
        return jdbc.query("""
                SELECT id, parent_department_id, code, name
                FROM departments
                ORDER BY display_order ASC, name ASC
                """, (rs, rowNum) -> new DepartmentLink(
                uuid(rs.getString("id")),
                uuid(rs.getString("parent_department_id")),
                rs.getString("code"),
                rs.getString("name")
        ));
    }

    public List<TeamLink> teamLinks() {
        return jdbc.query("""
                SELECT id, code, name
                FROM teams
                ORDER BY display_order ASC, name ASC
                """, (rs, rowNum) -> new TeamLink(
                uuid(rs.getString("id")),
                rs.getString("code"),
                rs.getString("name")
        ));
    }

    // Tech lead read scope: The employee is in a team the lead runs
    public boolean isMemberOfLedTeam(UUID techLeadId, UUID employeeId) {
        Boolean found = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM team_members tm
                    JOIN teams t ON t.id = tm.team_id
                    WHERE t.tech_lead_id = :techLeadId AND tm.user_id = :employeeId)
                """, new MapSqlParameterSource()
                .addValue("techLeadId", techLeadId.toString())
                .addValue("employeeId", employeeId.toString()), Boolean.class);
        return Boolean.TRUE.equals(found);
    }

    /*
     * ACTIVE CVs whose latest published version references each skill - the same scope as the skill
     * filter above, so the catalogue screen and the search never disagree.
     */
    public Map<UUID, Long> countCvsBySkill(Collection<UUID> skillIds) {
        Map<UUID, Long> result = new HashMap<>();
        if (skillIds.isEmpty()) {
            return result;
        }
        String sql = """
                SELECT jt.skill_id, COUNT(DISTINCT c.id) AS cv_count
                FROM cvs c
                JOIN cv_profiles cp ON cp.id = c.profile_id
                JOIN cv_versions lv ON lv.cv_id = c.id
                     AND lv.version_number = (SELECT MAX(v2.version_number) FROM cv_versions v2 WHERE v2.cv_id = c.id)
                CROSS JOIN JSON_TABLE(lv.content_json, '%s'
                     COLUMNS (skill_id VARCHAR(36) PATH '$.%s')) AS jt
                WHERE c.lifecycle_status = :active
                  AND cp.lifecycle_status = :active
                  AND jt.skill_id IN (:skillIds)
                GROUP BY jt.skill_id
                """.formatted(SKILL_ENTRIES_PATH, CvContentKeys.SKILL_ID);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("active", LifecycleStatus.ACTIVE.name())
                .addValue("skillIds", strings(skillIds));

        jdbc.query(sql, params, (RowCallbackHandler) rs ->
                result.put(uuid(rs.getString("skill_id")), rs.getLong("cv_count")));
        return result;
    }

    // ---------- WHERE ----------

    private String whereOf(CvSearchCriteria criteria, MapSqlParameterSource params) {
        params.addValue("active", LifecycleStatus.ACTIVE.name());

        List<String> clauses = new ArrayList<>();
        clauses.add("c.lifecycle_status = :active");
        clauses.add("cp.lifecycle_status = :active");

        addScope(criteria, params, clauses);
        addOrganization(criteria, params, clauses);
        addContent(criteria, params, clauses);

        return " WHERE " + String.join(" AND ", clauses);
    }

    private void addScope(CvSearchCriteria criteria, MapSqlParameterSource params, List<String> clauses) {
        params.addValue("actorId", criteria.actorId().toString());
        switch (criteria.scope()) {
            case ALL -> {
                // Admin and HR read the whole company
            }
            // Own CVs plus members of the lead teams - by membership, not by profile team
            case LED_TEAMS -> clauses.add("""
                    (cp.employee_id = :actorId OR cp.employee_id IN (
                                            SELECT tm.user_id FROM team_members tm
                                            JOIN teams t ON t.id = tm.team_id
                                            WHERE t.tech_lead_id = :actorId))""");
            case OWN -> clauses.add("cp.employee_id = :actorId");
        }
    }

    // Organization criteria do not depend on any version, so never-published CVs stay in
    private void addOrganization(CvSearchCriteria criteria, MapSqlParameterSource params, List<String> clauses) {
        if (criteria.primaryOnly()) {
            clauses.add("cp.is_primary = TRUE");
        }
        if (criteria.employeeName() != null) {
            clauses.add("u.full_name LIKE :employeeName");
            params.addValue("employeeName", LikePatterns.contains(criteria.employeeName()));
        }
        if (criteria.profileName() != null) {
            clauses.add("cp.name LIKE :profileName");
            params.addValue("profileName", LikePatterns.contains(criteria.profileName()));
        }
        if (!criteria.departmentIds().isEmpty()) {
            // Primary department of the employee
            clauses.add("u.primary_department_id IN (:departmentIds)");
            params.addValue("departmentIds", strings(criteria.departmentIds()));
        }
        if (criteria.teamId() != null) {
            clauses.add("EXISTS (SELECT 1 FROM team_members tf WHERE tf.user_id = cp.employee_id AND tf.team_id = :teamId)");
            params.addValue("teamId", criteria.teamId().toString());
        }
        if (criteria.language() != null) {
            clauses.add("c.language = :language");
            params.addValue("language", criteria.language().name());
        }
    }

    // Latest published version only: No older version, no draft, no never-published CV
    private void addContent(CvSearchCriteria criteria, MapSqlParameterSource params, List<String> clauses) {
        if (!criteria.hasContentCriteria()) {
            return;
        }
        clauses.add("lv.id IS NOT NULL");
        addKeyword(criteria.contentTerms(), params, clauses);
        addSkills(criteria.skillIds(), params, clauses);
    }

    /*
     * Every term must appear. Full-text where the parser can do it; LIKE for short or punctuated
     * terms and for Japanese CVs, which have no word breaks for the InnoDB parser.
     */
    private void addKeyword(List<String> terms, MapSqlParameterSource params, List<String> clauses) {
        if (terms.isEmpty()) {
            return;
        }

        List<String> allLikes = new ArrayList<>();
        List<String> likesBesideFullText = new ArrayList<>();
        List<String> fullTextTerms = new ArrayList<>();

        for (int i = 0; i < terms.size(); i++) {
            String term = terms.get(i);
            String like = "lv.content_text LIKE :term" + i;
            params.addValue("term" + i, LikePatterns.contains(term));
            allLikes.add(like);

            if (isFullTextWord(term)) {
                // Required word, prefix match: "Kubernet" finds "Kubernetes"
                fullTextTerms.add("+" + term + "*");
            } else {
                likesBesideFullText.add(like);
            }
        }

        String everyLike = String.join(" AND ", allLikes);
        if (fullTextTerms.isEmpty()) {
            clauses.add("(" + everyLike + ")");
            return;
        }

        params.addValue("fullText", String.join(" ", fullTextTerms));
        params.addValue("ja", Language.JA.name());

        List<String> nonJapanese = new ArrayList<>();
        nonJapanese.add("MATCH(lv.content_text) AGAINST(:fullText IN BOOLEAN MODE)");
        nonJapanese.addAll(likesBesideFullText);

        clauses.add("((c.language <> :ja AND " + String.join(" AND ", nonJapanese) + ")"
                + " OR (c.language = :ja AND " + everyLike + "))");
    }

    // Every selected skill must be referenced by the latest version
    private void addSkills(List<UUID> skillIds, MapSqlParameterSource params, List<String> clauses) {
        for (int i = 0; i < skillIds.size(); i++) {
            clauses.add("JSON_CONTAINS(JSON_EXTRACT(lv.content_json, '" + SKILL_IDS_PATH + "'), JSON_QUOTE(:skill" + i + "))");
            params.addValue("skill" + i, skillIds.get(i).toString());
        }
    }

    private static boolean isFullTextWord(String term) {
        return term.codePointCount(0, term.length()) >= FULLTEXT_MIN_TOKEN
                && FULLTEXT_WORD.matcher(term).matches();
    }

    // ---------- ORDER BY ----------

    private String orderByOf(Sort sort) {
        List<String> parts = new ArrayList<>();
        for (Sort.Order order : sort) {
            CvSearchSortField.fromProperty(order.getProperty())
                    .ifPresent(field -> parts.addAll(orderOf(field, order.getDirection())));
        }
        parts.addAll(TIE_BREAKERS);
        return " ORDER BY " + String.join(", ", parts);
    }

    private static List<String> orderOf(CvSearchSortField field, Sort.Direction direction) {
        return switch (field) {
            case EMPLOYEE_NAME -> List.of("u.full_name " + direction.name());
            // Never-published CVs last in both directions
            case PUBLISHED_AT -> List.of("(lv.published_at IS NULL) ASC", "lv.published_at " + direction.name());
        };
    }

    // ---------- Mapping ----------

    private CvSearchRow toRow(ResultSet rs) throws SQLException {
        return new CvSearchRow(
                uuid(rs.getString("cv_id")),
                Language.valueOf(rs.getString("language")),
                rs.getString("profile_name"),
                rs.getBoolean("is_primary"),
                uuid(rs.getString("employee_id")),
                rs.getString("full_name"),
                rs.getString("email"),
                uuid(rs.getString("avatar_image_id")),
                rs.getString("department_code"),
                rs.getString("department_name"),
                rs.getObject("version_number", Integer.class),
                rs.getObject("published_at", LocalDateTime.class),
                skillIdsOf(rs.getString("skill_ids"))
        );
    }

    // JSON_EXTRACT result; entries not linked to the catalogue hold null and are skipped
    private List<UUID> skillIdsOf(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            Stream<JsonNode> values = node.isArray()
                    ? StreamSupport.stream(node.spliterator(), false)
                    : Stream.of(node);
            return values
                    .filter(JsonNode::isTextual)
                    .map(value -> parseUuid(value.asText()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static List<String> strings(Collection<UUID> ids) {
        return ids.stream().map(UUID::toString).toList();
    }
}
