package org.vectora.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class RowLookupServlet extends HttpServlet {

    private final LuceneService luceneService;

    public RowLookupServlet(LuceneService luceneService) {
        this.luceneService = luceneService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        if (path == null || path.isBlank() || "/".equals(path)) {
            notFound(resp);
            return;
        }
        String[] parts = path.split("/");
        if (parts.length < 3) {
            notFound(resp);
            return;
        }
        String table = parts[1];
        String id = parts[2];
        if (table.isBlank() || id.isBlank()) {
            notFound(resp);
            return;
        }

        Optional<Map<String, Object>> row = luceneService.lookup(table, id);
        if (row.isEmpty()) {
            notFound(resp);
            return;
        }
        JsonUtil.writePrettyJson(resp, HttpServletResponse.SC_OK, row.get());
    }

    private void notFound(HttpServletResponse resp) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", "Not found");
        JsonUtil.writePrettyJson(resp, HttpServletResponse.SC_NOT_FOUND, payload);
    }
}
