package org.vectora.server;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public record AppConfig(ServerConfig server, LuceneConfig lucene, MysqlConfig mysql) {

    public static AppConfig load(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("Config path is required");
        }
        if (!Files.exists(path)) {
            throw new IOException("Config file not found: " + path);
        }
        Yaml yaml = new Yaml();
        try (InputStream input = Files.newInputStream(path)) {
            Map<String, Object> root = yaml.load(input);
            if (root == null) {
                root = new LinkedHashMap<>();
            }
            Map<String, Object> serverMap = map(root, "server");
            Map<String, Object> luceneMap = map(root, "lucene");
            Map<String, Object> mysqlMap = map(root, "mysql");

            ServerConfig server = new ServerConfig(
                    string(serverMap, "host", "0.0.0.0"),
                    integer(serverMap, "port", 8080)
            );
            LuceneConfig lucene = new LuceneConfig(
                    string(luceneMap, "index_path", "./data/index")
            );
            MysqlConfig mysql = new MysqlConfig(
                    string(mysqlMap, "host", null),
                    integer(mysqlMap, "port", 3306),
                    string(mysqlMap, "database", null),
                    string(mysqlMap, "username", null),
                    string(mysqlMap, "password", null)
            );

            return new AppConfig(server, lucene, mysql);
        }
    }

    public record ServerConfig(String host, int port) {}

    public record LuceneConfig(String indexPath) {}

    public record MysqlConfig(String host, int port, String database, String username, String password) {
        public String jdbcUrl() {
            String hostPart = host == null || host.isBlank() ? "127.0.0.1" : host;
            String dbPart = database == null ? "" : database;
            return "jdbc:mysql://" + hostPart + ":" + port + "/" + dbPart
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        }
    }

    private static Map<String, Object> map(Map<String, Object> root, String key) {
        if (root == null) {
            return new LinkedHashMap<>();
        }
        Object value = root.get(key);
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static String string(Map<String, Object> map, String key, String fallback) {
        if (map == null) {
            return fallback;
        }
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        String str = value.toString().trim();
        return str.isEmpty() ? fallback : str;
    }

    private static int integer(Map<String, Object> map, String key, int fallback) {
        if (map == null) {
            return fallback;
        }
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
