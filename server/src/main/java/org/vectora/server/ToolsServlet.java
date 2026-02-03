package org.vectora.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ToolsServlet extends HttpServlet {

    private final LuceneService luceneService;

    public ToolsServlet(LuceneService luceneService) {
        this.luceneService = luceneService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            List<Map<String, Object>> tables = luceneService.listTables();
            List<Map<String, Object>> tools = new ArrayList<>();
            for (Map<String, Object> table : tables) {
                String tableName = value(table.get("table"));
                if (tableName.isBlank()) {
                    continue;
                }
                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("table", tableName);
                Map<String, Object> endpoints = new LinkedHashMap<>();
                endpoints.put("search", "/search?table=" + tableName);
                endpoints.put("lookup", "/" + tableName + "/{id}");
                tool.put("endpoints", endpoints);
                tools.add(tool);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tools", tools);
            JsonUtil.writePrettyJson(resp, HttpServletResponse.SC_OK, payload);
        } catch (Exception e) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Failed to list tools");
            payload.put("message", e.getMessage());
            JsonUtil.writePrettyJson(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, payload);
        }
    }

    private String value(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    private boolean booleanValue(Object obj) {
        if (obj instanceof Boolean bool) {
            return bool;
        }
        return obj != null && "true".equalsIgnoreCase(obj.toString());
    }
}
