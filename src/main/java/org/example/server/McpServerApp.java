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

public class McpServerApp {

    private static final Logger LOGGER = initLogger();
    private static final String SERVER_NAME = "mcp-demo-server";
    private static final String SERVER_VERSION = "1.0.0";

    private static Logger initLogger() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss.SSS");
        return LoggerFactory.getLogger(McpServerApp.class);
    }

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

        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .tool(pingTool(jsonMapper), (exchange, params) -> handlePing())
                .tool(pongTool(jsonMapper), (exchange, params) -> handlePong())
                .tool(dingTool(jsonMapper), (exchange, params) -> handleDing())
                .tool(dongTool(jsonMapper), (exchange, params) -> handleDong())
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
        LOGGER.info("MCP server started on {}", baseUrl);
        httpServer.join();
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

    private static McpSchema.CallToolResult toolResult(String toolName, String responsePrefix, boolean reverseTime) {
        String serverTime = java.time.OffsetDateTime.now().toString();
        String timeForResponse = reverseTime
                ? new StringBuilder(serverTime).reverse().toString()
                : serverTime;
        LOGGER.info("Received {} request at {}", toolName, serverTime);
        return new McpSchema.CallToolResult(responsePrefix + " @ " + timeForResponse, false);
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
