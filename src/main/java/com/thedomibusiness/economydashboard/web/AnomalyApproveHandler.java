package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.anomaly.Anomaly;
import com.thedomibusiness.economydashboard.anomaly.AnomalyApprovalService;

import java.io.IOException;
import java.util.Map;

/** POST /api/anomalies/approve?key=...&severity=...&category=...&title=...&description=...&linkUrl=...
 *  Marks one anomaly (identified by its key) as reviewed - it stops reappearing in the
 *  "Handlungsbedarf" panel and shows up in the archive instead. The full text is sent by the
 *  frontend (not re-looked-up server-side) so the archive keeps the exact wording the reviewer saw. */
public class AnomalyApproveHandler implements HttpHandler {

    private final AnomalyApprovalService approvalService;

    public AnomalyApproveHandler(AnomalyApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        Map<String, String> params = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
        String key = params.get("key");
        String severityStr = params.get("severity");
        String category = params.get("category");
        String title = params.get("title");
        String description = params.get("description");
        String linkUrl = params.get("linkUrl");

        if (key == null || severityStr == null || category == null || title == null || description == null) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }
        try {
            Anomaly.Severity.valueOf(severityStr);
        } catch (IllegalArgumentException e) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }

        approvalService.approve(key, severityStr, category, title, description, linkUrl);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}
