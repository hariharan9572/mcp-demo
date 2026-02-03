package org.vectora.server;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class McpServerApp {

    private static final Logger LOGGER = initLogger();
    private static final String SERVER_NAME = "mcp-lucene-server";
    private static final String SERVER_VERSION = "1.0.0";

    private static Logger initLogger() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");
        return LoggerFactory.getLogger(McpServerApp.class);
    }

    public static void main(String[] args) throws Exception {
        Args parsed = Args.parse(args);
        Path configPath = Path.of(parsed.configPath);
        AppConfig config = AppConfig.load(configPath);

        if (parsed.ingestOnly) {
            new LuceneIndexer().buildIndex(config);
            LOGGER.info("Lucene ingest complete. Index stored at {}", config.lucene().indexPath());
            return;
        }

        Path indexPath = Path.of(config.lucene().indexPath());
        if (!indexExists(indexPath)) {
            LOGGER.info("Lucene index missing; running ingest.");
            new LuceneIndexer().buildIndex(config);
        }

        try (LuceneService luceneService = new LuceneService(indexPath)) {
            int port = config.server().port();
            String baseUrl = "http://localhost:" + port;

            McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
            HttpServletSseServerTransportProvider transportProvider =
                    HttpServletSseServerTransportProvider.builder()
                            .jsonMapper(jsonMapper)
                            .baseUrl(baseUrl + "/mcp")
                            .sseEndpoint("/sse")
                            .messageEndpoint("/message")
                            .build();

            McpSyncServer server = buildMcpServer(luceneService, jsonMapper, transportProvider);

            Server httpServer = new Server(port);
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
            context.setContextPath("/");
            context.addServlet(new ServletHolder(transportProvider), "/mcp/*");
            context.addServlet(new ServletHolder(new ToolsServlet(luceneService)), "/tools");
            context.addServlet(new ServletHolder(new HealthServlet()), "/health");
            context.addServlet(new ServletHolder(new SearchServlet(luceneService)), "/search");
            context.addServlet(new ServletHolder(new RowLookupServlet(luceneService)), "/*");
            httpServer.setHandler(context);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    httpServer.stop();
                } catch (Exception e) {
                    LOGGER.warn("Failed to stop HTTP server cleanly: {}", e.getMessage());
                }
                server.closeGracefully();
            }));

            httpServer.start();
            LOGGER.info("Server started on {}", baseUrl);
            httpServer.join();
        }
    }

    private static McpSyncServer buildMcpServer(LuceneService luceneService, McpJsonMapper jsonMapper,
                                                HttpServletSseServerTransportProvider transportProvider)
            throws Exception {
        var builder = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .tool(pingTool(jsonMapper), (exchange, params) -> handlePing())
                .tool(pongTool(jsonMapper), (exchange, params) -> handlePong())
                .tool(dingTool(jsonMapper), (exchange, params) -> handleDing())
                .tool(dongTool(jsonMapper), (exchange, params) -> handleDong());

        List<Map<String, Object>> tables = luceneService.listTables();
        for (Map<String, Object> table : tables) {
            String tableName = value(table.get("table"));
            if (tableName.isBlank()) {
                continue;
            }
            builder = builder.tool(tableSearchTool(jsonMapper, tableName),
                    (exchange, params) -> handleTableSearch(luceneService, tableName, params));
            builder = builder.tool(tableLookupTool(jsonMapper, tableName),
                    (exchange, params) -> handleTableLookup(luceneService, tableName, params));
        }

        return builder.build();
    }

    private static McpSchema.Tool pingTool(McpJsonMapper jsonMapper) {
        return buildTool(jsonMapper, "ping", "Returns pong with server time");
    }

    private static McpSchema.Tool pongTool(McpJsonMapper jsonMapper) {
        return buildTool(jsonMapper, "pong", "Returns ping with server time");
    }

    private static McpSchema.Tool dingTool(McpJsonMapper jsonMapper) {
        return buildTool(jsonMapper, "ding", "Returns dong with reversed server time");
    }

    private static McpSchema.Tool dongTool(McpJsonMapper jsonMapper) {
        return buildTool(jsonMapper, "dong", "Returns ding with reversed server time");
    }

    private static McpSchema.Tool buildTool(McpJsonMapper jsonMapper, String name, String description) {
        return McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(jsonMapper, """
                        {
                          "type": "object",
                          "properties": {
                            "message": {
                              "type": "string"
                            }
                          },
                          "additionalProperties": false
                        }
                        """)
                .build();
    }

    private static McpSchema.Tool tableSearchTool(McpJsonMapper jsonMapper, String table) {
        return McpSchema.Tool.builder()
                .name("search_" + normalize(table))
                .description("Search rows in " + table + " using Lucene")
                .inputSchema(jsonMapper, """
                        {
                          "type": "object",
                          "properties": {
                            "query": {"type": "string"},
                            "created_at_from": {"type": "string"},
                            "created_at_to": {"type": "string"},
                            "limit": {"type": "integer"}
                          },
                          "additionalProperties": false
                        }
                        """)
                .build();
    }

    private static McpSchema.Tool tableLookupTool(McpJsonMapper jsonMapper, String table) {
        return McpSchema.Tool.builder()
                .name("get_" + normalize(table) + "_by_id")
                .description("Lookup a " + table + " row by id")
                .inputSchema(jsonMapper, """
                        {
                          "type": "object",
                          "properties": {
                            "id": {"type": "string"}
                          },
                          "additionalProperties": false
                        }
                        """)
                .build();
    }

    private static McpSchema.CallToolResult handlePing() {
        return toolResult("ping", "pong", false);
    }

    private static McpSchema.CallToolResult handlePong() {
        return toolResult("pong", "ping", false);
    }

    private static McpSchema.CallToolResult handleDing() {
        return toolResult("ding", "dong", true);
    }

    private static McpSchema.CallToolResult handleDong() {
        return toolResult("dong", "ding", true);
    }

    private static McpSchema.CallToolResult handleTableSearch(LuceneService luceneService, String table,
                                                              Object params) {
        try {
            Map<String, Object> args = paramsToMap(params);
            String query = value(args.get("query"));
            String createdFromRaw = value(args.get("created_at_from"));
            String createdToRaw = value(args.get("created_at_to"));
            int limit = parseLimit(args.get("limit"));
            Long createdFrom = LuceneService.parseTime(createdFromRaw);
            Long createdTo = LuceneService.parseTime(createdToRaw);
            Map<String, Object> payload = luceneService.search(query, table, createdFrom, createdTo, limit);
            return new McpSchema.CallToolResult(JsonUtil.MAPPER.writeValueAsString(payload), false);
        } catch (Exception e) {
            return new McpSchema.CallToolResult("Search failed: " + e.getMessage(), true);
        }
    }

    private static McpSchema.CallToolResult handleTableLookup(LuceneService luceneService, String table,
                                                              Object params) {
        try {
            Map<String, Object> args = paramsToMap(params);
            String id = value(args.get("id"));
            if (id.isBlank()) {
                return new McpSchema.CallToolResult("Missing id", true);
            }
            var row = luceneService.lookup(table, id);
            if (row.isEmpty()) {
                return new McpSchema.CallToolResult("Not found", true);
            }
            return new McpSchema.CallToolResult(JsonUtil.MAPPER.writeValueAsString(row.get()), false);
        } catch (Exception e) {
            return new McpSchema.CallToolResult("Lookup failed: " + e.getMessage(), true);
        }
    }

    private static McpSchema.CallToolResult toolResult(String toolName, String responsePrefix, boolean reverseTime) {
        String serverTime = java.time.OffsetDateTime.now().toString();
        String timeForResponse = reverseTime
                ? new StringBuilder(serverTime).reverse().toString()
                : serverTime;
        LOGGER.info("Received {} request at {}", toolName, serverTime);
        return new McpSchema.CallToolResult(responsePrefix + " @ " + timeForResponse, false);
    }

    private static boolean indexExists(Path path) throws Exception {
        if (!Files.exists(path)) {
            return false;
        }
        try (var directory = org.apache.lucene.store.FSDirectory.open(path)) {
            return org.apache.lucene.index.DirectoryReader.indexExists(directory);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String value(Object obj) {
        return obj == null ? "" : obj.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paramsToMap(Object params) {
        if (params instanceof Map<?, ?> raw) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() != null) {
                    out.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return out;
        }
        return new LinkedHashMap<>();
    }

    private static int parseLimit(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 50;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    private record Args(String configPath, boolean ingestOnly) {
        static Args parse(String[] args) {
            String configPath = "./config.yaml";
            boolean ingestOnly = false;
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    String arg = args[i];
                    if (arg == null) {
                        continue;
                    }
                    if (arg.startsWith("--config=")) {
                        configPath = arg.substring("--config=".length());
                        continue;
                    }
                    if ("--config".equals(arg) && i + 1 < args.length) {
                        configPath = args[++i];
                        continue;
                    }
                    if ("--ingest".equals(arg)) {
                        ingestOnly = true;
                    }
                }
            }
            return new Args(configPath, ingestOnly);
        }
    }
}
