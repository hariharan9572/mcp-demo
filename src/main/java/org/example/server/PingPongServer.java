package org.example.server;

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

public class PingPongServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PingPongServer.class);

    public static void main(String[] args) throws Exception {
        int port = resolvePort(args);
        String baseUrl = "http://localhost:" + port;

        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();
        HttpServletSseServerTransportProvider transportProvider =
                HttpServletSseServerTransportProvider.builder()
                        .jsonMapper(jsonMapper)
                        .baseUrl(baseUrl)
                        .sseEndpoint("/sse")
                        .messageEndpoint("/message")
                        .build();

        McpSchema.Tool pingTool = McpSchema.Tool.builder()
                .name("ping")
                .description("Simple ping-pong test tool")
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

        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("ping-pong-server", "1.0.0")
                .tool(pingTool, (exchange, params) -> {
                    String serverTime = java.time.OffsetDateTime.now().toString();
                    LOGGER.info("Received ping request at {}", serverTime);
                    return new McpSchema.CallToolResult("pong @ " + serverTime, false);
                })
                .build();

        Server httpServer = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        context.addServlet(new ServletHolder(transportProvider), "/*");
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
        LOGGER.info("MCP Ping-Pong Server started on {}", baseUrl);
        httpServer.join();
    }

    private static int resolvePort(String[] args) {
        if (args != null && args.length > 0 && !args[0].isBlank()) {
            return Integer.parseInt(args[0]);
        }
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            return Integer.parseInt(envPort);
        }
        return 8080;
    }
}
