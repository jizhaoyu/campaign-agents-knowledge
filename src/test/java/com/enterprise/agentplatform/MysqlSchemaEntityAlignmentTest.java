package com.enterprise.agentplatform;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.agentplatform.domain.entity.ApprovalTask;
import com.enterprise.agentplatform.domain.entity.AuditLog;
import com.enterprise.agentplatform.domain.entity.AuthTokenSession;
import com.enterprise.agentplatform.domain.entity.Conversation;
import com.enterprise.agentplatform.domain.entity.DocumentChunk;
import com.enterprise.agentplatform.domain.entity.DocumentIndexTask;
import com.enterprise.agentplatform.domain.entity.DocumentRecord;
import com.enterprise.agentplatform.domain.entity.KnowledgeBase;
import com.enterprise.agentplatform.domain.entity.MessageRecord;
import com.enterprise.agentplatform.domain.entity.Role;
import com.enterprise.agentplatform.domain.entity.Ticket;
import com.enterprise.agentplatform.domain.entity.UserAccount;
import com.enterprise.agentplatform.domain.entity.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MysqlSchemaEntityAlignmentTest {

    private static final Path MYSQL_MIGRATIONS = Path.of("src", "main", "resources", "db", "mysql");
    private static final List<Class<?>> ENTITIES = List.of(
            ApprovalTask.class,
            AuditLog.class,
            AuthTokenSession.class,
            Conversation.class,
            DocumentChunk.class,
            DocumentIndexTask.class,
            DocumentRecord.class,
            KnowledgeBase.class,
            MessageRecord.class,
            Role.class,
            Ticket.class,
            UserAccount.class,
            UserRole.class
    );
    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?is)create\\s+table\\s+`?([a-zA-Z0-9_]+)`?\\s*\\((.*?)\\)\\s*engine"
    );
    private static final Pattern ADD_COLUMN = Pattern.compile(
            "(?is)alter\\s+table\\s+`?([a-zA-Z0-9_]+)`?\\s+add\\s+column\\s+`?([a-zA-Z0-9_]+)`?"
    );

    @Test
    void mysqlMigrationsShouldCoverAllJpaEntityTablesAndColumns() throws IOException {
        Map<String, Set<String>> schema = parseMysqlSchema();

        assertThat(schema)
                .as("MySQL migration tables")
                .containsKeys(ENTITIES.stream().map(this::tableName).toArray(String[]::new));

        Map<String, List<String>> missingColumnsByTable = new LinkedHashMap<>();
        for (Class<?> entity : ENTITIES) {
            String tableName = tableName(entity);
            Set<String> actualColumns = schema.getOrDefault(tableName, Set.of());
            List<String> missingColumns = persistentColumns(entity).stream()
                    .filter(column -> !actualColumns.contains(column))
                    .toList();
            if (!missingColumns.isEmpty()) {
                missingColumnsByTable.put(tableName, missingColumns);
            }
        }

        assertThat(missingColumnsByTable)
                .as("JPA entity columns missing from src/main/resources/db/mysql migrations")
                .isEmpty();
    }

    private Map<String, Set<String>> parseMysqlSchema() throws IOException {
        Map<String, Set<String>> schema = new LinkedHashMap<>();
        for (Path migration : mysqlMigrationFiles()) {
            String sql = Files.readString(migration);
            collectCreateTableColumns(sql, schema);
            collectAlterTableColumns(sql, schema);
        }
        return schema;
    }

    private List<Path> mysqlMigrationFiles() throws IOException {
        try (Stream<Path> paths = Files.list(MYSQL_MIGRATIONS)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private void collectCreateTableColumns(String sql, Map<String, Set<String>> schema) {
        Matcher matcher = CREATE_TABLE.matcher(sql);
        while (matcher.find()) {
            String tableName = normalize(matcher.group(1));
            Set<String> columns = schema.computeIfAbsent(tableName, ignored -> new LinkedHashSet<>());
            for (String line : matcher.group(2).split("\\R")) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || isTableConstraint(trimmed)) {
                    continue;
                }
                columns.add(normalize(trimmed.split("\\s+", 2)[0].replace("`", "").replace(",", "")));
            }
        }
    }

    private void collectAlterTableColumns(String sql, Map<String, Set<String>> schema) {
        Matcher matcher = ADD_COLUMN.matcher(sql);
        while (matcher.find()) {
            schema.computeIfAbsent(normalize(matcher.group(1)), ignored -> new LinkedHashSet<>())
                    .add(normalize(matcher.group(2)));
        }
    }

    private boolean isTableConstraint(String line) {
        String normalized = normalize(line);
        return normalized.startsWith("primary ")
                || normalized.startsWith("unique ")
                || normalized.startsWith("key ")
                || normalized.startsWith("constraint ")
                || normalized.startsWith("index ");
    }

    private String tableName(Class<?> entity) {
        assertThat(entity.isAnnotationPresent(Entity.class))
                .as("%s should be a JPA entity", entity.getName())
                .isTrue();
        Table table = entity.getAnnotation(Table.class);
        if (table != null && !table.name().isBlank()) {
            return normalize(table.name());
        }
        return camelToSnake(entity.getSimpleName());
    }

    private List<String> persistentColumns(Class<?> entity) {
        List<String> columns = new ArrayList<>();
        Class<?> current = entity;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (isPersistentField(field)) {
                    columns.add(columnName(field));
                }
            }
            current = current.getSuperclass();
        }
        return columns;
    }

    private boolean isPersistentField(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers)
                && !Modifier.isTransient(modifiers)
                && !field.isSynthetic()
                && !field.isAnnotationPresent(Transient.class)
                && (field.isAnnotationPresent(Column.class) || field.isAnnotationPresent(Id.class));
    }

    private String columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        if (column != null && !column.name().isBlank()) {
            return normalize(column.name());
        }
        return camelToSnake(field.getName());
    }

    private String camelToSnake(String value) {
        return normalize(value.replaceAll("([a-z0-9])([A-Z])", "$1_$2"));
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
