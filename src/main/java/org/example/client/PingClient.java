package org.example.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PingClient {

    public static void main(String[] args) throws Exception {

        McpJsonMapper jsonMapper = McpJsonMapper.getDefault();

        // Spawn the server directly to keep stdio free of Maven logs.
        String classpath = buildClasspath();
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ServerParameters serverParameters = ServerParameters.builder(javaBin)
                .args(
                        "-cp",
                        classpath,
                        "org.example.server.PingPongServer"
                )
                .build();

        StdioClientTransport transport = new StdioClientTransport(serverParameters, jsonMapper);
        transport.setStdErrorHandler(line -> System.err.println("[server] " + line));

        McpSyncClient client = McpClient.sync(transport).build();
        try {
            client.initialize();

            McpSchema.CallToolResult response =
                    client.callTool(new McpSchema.CallToolRequest("ping", Map.of()));

            System.out.println("✅ Server response: " + response);
        } finally {
            client.closeGracefully();
        }
    }

    private static String buildClasspath() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl instanceof URLClassLoader urlClassLoader) {
            return Stream.of(urlClassLoader.getURLs())
                    .map(PingClient::urlToPath)
                    .collect(Collectors.joining(File.pathSeparator));
        }
        return System.getProperty("java.class.path");
    }

    private static String urlToPath(URL url) {
        try {
            return Path.of(url.toURI()).toString();
        } catch (Exception e) {
            // Fall back to the raw path if URI conversion fails.
            return url.getPath();
        }
    }
}
