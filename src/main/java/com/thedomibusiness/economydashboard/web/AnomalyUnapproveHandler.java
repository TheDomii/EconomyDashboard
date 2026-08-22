package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.anomaly.AnomalyApprovalService;

import java.io.IOException;
import java.util.Map;

/** POST /api/anomalies/unapprove?key=... - removes one entry from the archive. If the underlying
 *  condition still exists, it reappears in "Handlungsbedarf" on the next detection cycle. */
public class AnomalyUnapproveHandler implements HttpHandler {

    private final AnomalyApprovalService approvalService;

    public AnomalyUnapproveHandler(AnomalyApprovalService approvalService) {
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
        if (key == null) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
        }

        approvalService.unapprove(key);
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }
}
