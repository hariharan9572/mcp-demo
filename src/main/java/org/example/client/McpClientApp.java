package org.example.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;

public class McpClientApp {

    private static final Logger LOGGER = initLogger();

    private static Logger initLogger() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");
        return LoggerFactory.getLogger(McpClientApp.class);
    }

    public static void main(String[] args) throws Exception {
        ClientConfig config = resolveConfig(args);
        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(config.baseUrl)
                .jsonMapper(jsonMapper)
                .build();

        McpSyncClient client = McpClient.sync(transport).build();
        try {
            client.initialize();
            if ("list".equals(config.toolName)) {
                McpSchema.ListToolsResult listToolsResult = client.listTools();
                LOGGER.info(formatToolList(listToolsResult));
            } else {
                McpSchema.CallToolResult response = dispatchTool(client, config);
                LOGGER.info("Server response: {}", response);
            }
        } finally {
            client.closeGracefully();
        }
    }

    private static McpSchema.CallToolResult dispatchTool(McpSyncClient client, ClientConfig config) {
        return switch (config.toolName) {
            case "ping" -> callPing(client, config.message);
            case "pong" -> callPong(client, config.message);
            case "ding" -> callDing(client, config.message);
            case "dong" -> callDong(client, config.message);
            default -> throw new IllegalArgumentException("Unknown tool: " + config.toolName);
        };
    }

    private static McpSchema.CallToolResult callPing(McpSyncClient client, String message) {
        return callTool(client, "ping", message);
    }

    private static McpSchema.CallToolResult callPong(McpSyncClient client, String message) {
        return callTool(client, "pong", message);
    }

    private static McpSchema.CallToolResult callDing(McpSyncClient client, String message) {
        return callTool(client, "ding", message);
    }

    private static McpSchema.CallToolResult callDong(McpSyncClient client, String message) {
        return callTool(client, "dong", message);
    }

    private static McpSchema.CallToolResult callTool(McpSyncClient client, String toolName, String message) {
        return client.callTool(new McpSchema.CallToolRequest(toolName, Map.of("message", message)));
    }

    private static ClientConfig resolveConfig(String[] args) {
        String toolName = "ping";
        String message = "ping";
        String baseUrl = "http://localhost:8080";

        if (args != null && args.length == 1 && args[0] != null) {
            String arg = args[0].trim();
            if (arg.matches("\\d+")) {
                baseUrl = "http://localhost:" + arg;
            } else if (arg.startsWith("http://") || arg.startsWith("https://")) {
                baseUrl = arg;
            } else if (!arg.isEmpty()) {
                toolName = normalizeTool(arg);
                message = arg;
            }
        } else if (args != null && args.length >= 2) {
            String argTool = args[0];
            if (argTool != null && !argTool.isBlank()) {
                toolName = normalizeTool(argTool);
                message = argTool.trim();
            }
            String argBase = args[1];
            if (argBase != null && !argBase.isBlank()) {
                String trimmed = argBase.trim();
                if (trimmed.matches("\\d+")) {
                    baseUrl = "http://localhost:" + trimmed;
                } else {
                    baseUrl = trimmed;
                }
            }
        }

        return new ClientConfig(toolName, message, baseUrl);
    }

    private static String normalizeTool(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ping", "pong", "ding", "dong", "list" -> normalized;
            default -> "ping";
        };
    }

    private static String formatToolList(McpSchema.ListToolsResult listToolsResult) {
        StringBuilder builder = new StringBuilder();
        builder.append(System.lineSeparator()).append("Available tools:");
        if (listToolsResult == null || listToolsResult.tools() == null || listToolsResult.tools().isEmpty()) {
            builder.append(" (none)");
            return builder.toString();
        }
        for (McpSchema.Tool tool : listToolsResult.tools()) {
            builder.append(System.lineSeparator()).append("- ").append(tool.name());
            String description = tool.description();
            if (description != null && !description.isBlank()) {
                builder.append(": ").append(description);
            }
        }
        return builder.toString();
    }

    private record ClientConfig(String toolName, String message, String baseUrl) {}
}
