package org.vectora.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SearchServlet extends HttpServlet {

    private final LuceneService luceneService;

    public SearchServlet(LuceneService luceneService) {
        this.luceneService = luceneService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String query = value(req.getParameter("query"));
        if (query.isBlank()) {
            query = value(req.getParameter("q"));
        }
        String table = value(req.getParameter("table"));
        String createdFromRaw = value(req.getParameter("created_at_from"));
        String createdToRaw = value(req.getParameter("created_at_to"));
        int limit = parseLimit(req.getParameter("limit"));

        try {
            Long createdFrom = LuceneService.parseTime(createdFromRaw);
            Long createdTo = LuceneService.parseTime(createdToRaw);
            Map<String, Object> payload = luceneService.search(query, table, createdFrom, createdTo, limit);
            JsonUtil.writePrettyJson(resp, HttpServletResponse.SC_OK, payload);
        } catch (Exception e) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("error", "Search failed");
            payload.put("message", e.getMessage());
            JsonUtil.writePrettyJson(resp, HttpServletResponse.SC_BAD_REQUEST, payload);
        }
    }

    private int parseLimit(String value) {
        if (value == null || value.isBlank()) {
            return 50;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
