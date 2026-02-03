package org.vectora.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LuceneIndexer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneIndexer.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void buildIndex(AppConfig config) throws IOException, SQLException {
        validateConfig(config);
        Path indexPath = Path.of(config.lucene().indexPath());
        Files.createDirectories(indexPath);

        try (Directory directory = FSDirectory.open(indexPath);
             Analyzer analyzer = new StandardAnalyzer();
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer)
                     .setOpenMode(IndexWriterConfig.OpenMode.CREATE))) {

            try (Connection connection = DriverManager.getConnection(
                    config.mysql().jdbcUrl(),
                    config.mysql().username(),
                    config.mysql().password())) {
                DatabaseMetaData metaData = connection.getMetaData();
                List<String> tableNames = loadTableNames(metaData, connection.getCatalog());
                for (String tableName : tableNames) {
                    indexTable(connection, metaData, tableName, writer);
                }
            }

            writer.commit();
        }
    }

    private void validateConfig(AppConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config is required");
        }
        AppConfig.MysqlConfig mysql = config.mysql();
        if (mysql == null || mysql.host() == null || mysql.database() == null
                || mysql.username() == null || mysql.password() == null) {
            throw new IllegalArgumentException("mysql config (host, database, username, password) is required");
        }
    }

    private List<String> loadTableNames(DatabaseMetaData metaData, String catalog) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (name != null && !name.isBlank()) {
                    tables.add(name);
                }
            }
        }
        return tables;
    }

    private void indexTable(Connection connection, DatabaseMetaData metaData, String table,
                            IndexWriter writer) throws SQLException, IOException {
        List<String> primaryKeys = loadPrimaryKeys(metaData, connection.getCatalog(), table);
        if (primaryKeys.isEmpty()) {
            LOGGER.warn("Skipping table {} because it has no primary key", table);
            return;
        }

        String sql = "SELECT * FROM `" + table + "`";
        long rowCount = 0;
        boolean hasCreatedAt = false;

        try (Statement stmt = connection.createStatement()) {
            stmt.setFetchSize(500);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData rsMeta = rs.getMetaData();
                int columnCount = rsMeta.getColumnCount();
                List<String> columnNames = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    String name = rsMeta.getColumnLabel(i);
                    if (name == null || name.isBlank()) {
                        name = rsMeta.getColumnName(i);
                    }
                    columnNames.add(name);
                    if ("created_at".equalsIgnoreCase(name)) {
                        hasCreatedAt = true;
                    }
                }

                while (rs.next()) {
                    String idValue = buildId(primaryKeys, rs);
                    if (idValue == null) {
                        continue;
                    }

                    Map<String, Object> row = new LinkedHashMap<>();
                    StringBuilder content = new StringBuilder();
                    Instant createdAt = null;
                    String createdAtRaw = null;

                    for (int i = 1; i <= columnCount; i++) {
                        String colName = columnNames.get(i - 1);
                        Object value = rs.getObject(i);
                        Object normalized = normalizeValue(value);
                        row.put(colName, normalized);
                        if (normalized != null) {
                            content.append(normalized.toString()).append(' ');
                        }
                        if ("created_at".equalsIgnoreCase(colName)) {
                            createdAtRaw = normalized == null ? null : normalized.toString();
                            createdAt = toInstant(value, createdAtRaw);
                        }
                    }

                    Document doc = new Document();
                    doc.add(new StringField("doc_type", "row", Field.Store.YES));
                    doc.add(new StringField("table", table, Field.Store.YES));
                    doc.add(new StringField("id", idValue, Field.Store.YES));
                    doc.add(new StoredField("data", objectMapper.writeValueAsString(row)));
                    doc.add(new TextField("content", content.toString(), Field.Store.NO));

                    if (createdAt != null) {
                        long epoch = createdAt.toEpochMilli();
                        doc.add(new LongPoint("created_at_epoch", epoch));
                        doc.add(new StoredField("created_at_epoch", epoch));
                        if (createdAtRaw != null) {
                            doc.add(new StoredField("created_at", createdAtRaw));
                        }
                    }

                    writer.addDocument(doc);
                    rowCount++;
                }
            }
        }

        Document metaDoc = new Document();
        metaDoc.add(new StringField("doc_type", "table_meta", Field.Store.YES));
        metaDoc.add(new StringField("table", table, Field.Store.YES));
        metaDoc.add(new StoredField("primary_key", String.join(",", primaryKeys)));
        metaDoc.add(new StoredField("row_count", rowCount));
        metaDoc.add(new StoredField("has_created_at", Boolean.toString(hasCreatedAt)));
        writer.addDocument(metaDoc);

        LOGGER.info("Indexed table {} (rows: {})", table, rowCount);
    }

    private List<String> loadPrimaryKeys(DatabaseMetaData metaData, String catalog, String table) throws SQLException {
        List<String> keys = new ArrayList<>();
        try (ResultSet rs = metaData.getPrimaryKeys(catalog, null, table)) {
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if (col != null && !col.isBlank()) {
                    keys.add(col);
                }
            }
        }
        return keys;
    }

    private String buildId(List<String> primaryKeys, ResultSet rs) throws SQLException {
        List<String> parts = new ArrayList<>();
        for (String key : primaryKeys) {
            Object value = rs.getObject(key);
            if (value == null) {
                return null;
            }
            parts.add(value.toString());
        }
        return String.join(":", parts);
    }

    private Instant toInstant(Object value, String raw) {
        if (value == null && raw == null) {
            return null;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        String candidate = raw != null ? raw : value.toString();
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        String trimmed = candidate.trim();
        try {
            return Instant.parse(trimmed);
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(trimmed).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
        }
        try {
            long epoch = Long.parseLong(trimmed);
            return Instant.ofEpochMilli(epoch);
        } catch (Exception ignored) {
        }
        return null;
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.time.LocalDateTime
                || value instanceof java.time.LocalDate
                || value instanceof java.time.LocalTime
                || value instanceof java.time.OffsetDateTime
                || value instanceof java.time.OffsetTime
                || value instanceof java.time.Instant) {
            return value.toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return new java.util.Date(sqlDate.getTime()).toInstant().toString();
        }
        if (value instanceof java.sql.Time sqlTime) {
            return new java.util.Date(sqlTime.getTime()).toInstant().toString();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().toString();
        }
        return value;
    }
}
