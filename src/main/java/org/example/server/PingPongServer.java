package org.example.server;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

public class PingPongServer {

    public static void main(String[] args) {
        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(jsonMapper);

        McpSchema.Tool pingTool = McpSchema.Tool.builder()
                .name("ping")
                .description("Simple ping-pong test tool")
                .inputSchema(jsonMapper, """
                        {
                          "type": "object",
                          "properties": {}
                        }
                        """)
                .build();

        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("ping-pong-server", "1.0.0")
                .tool(pingTool, (exchange, params) -> {
                    System.err.println("Received ping request");
                    return new McpSchema.CallToolResult("Hello World", false);
                })
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));
        System.err.println("MCP Ping-Pong Server started");
        // Keep the process alive to continue serving stdio requests.
        try {
            new java.util.concurrent.CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
