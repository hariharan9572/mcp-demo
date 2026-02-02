package org.example.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PingClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PingClient.class);

    public static void main(String[] args) throws Exception {
        ClientConfig config = resolveConfig(args);
        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();

        HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder(config.baseUrl)
                .jsonMapper(jsonMapper)
                .build();

        McpSyncClient client = McpClient.sync(transport).build();
        try {
            client.initialize();

            McpSchema.CallToolResult response =
                    client.callTool(new McpSchema.CallToolRequest("ping", Map.of("message", config.message)));

            LOGGER.info("Server response: {}", response);
        } finally {
            client.closeGracefully();
        }
    }

    private static ClientConfig resolveConfig(String[] args) {
        String message = "ping";
        String baseUrl = "http://localhost:8080";

        if (args != null && args.length == 1 && args[0] != null) {
            String arg = args[0].trim();
            if (arg.matches("\\d+")) {
                baseUrl = "http://localhost:" + arg;
            } else if (arg.startsWith("http://") || arg.startsWith("https://")) {
                baseUrl = arg;
            } else if (!arg.isEmpty()) {
                message = arg;
            }
        } else if (args != null && args.length >= 2) {
            String argMessage = args[0];
            if (argMessage != null && !argMessage.isBlank()) {
                message = argMessage.trim();
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

        return new ClientConfig(message, baseUrl);
    }

    private record ClientConfig(String message, String baseUrl) {}
}
