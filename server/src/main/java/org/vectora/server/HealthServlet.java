package org.vectora.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class HealthServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ok");
        JsonUtil.writeJson(resp, HttpServletResponse.SC_OK, payload);
    }
}
