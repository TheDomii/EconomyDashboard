package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.anomaly.AnomalyApprovalService;
import com.thedomibusiness.economydashboard.anomaly.ArchivedAnomaly;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class AnomalyArchiveApiHandler implements HttpHandler {

    private final AnomalyApprovalService approvalService;

    public AnomalyArchiveApiHandler(AnomalyApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<ArchivedAnomaly> archive = approvalService.listArchive();

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < archive.size(); i++) {
            ArchivedAnomaly a = archive.get(i);
            if (i > 0) json.append(",");
            json.append("{\"key\":").append(JsonUtil.quoteOrNull(a.key)).append(",")
                    .append("\"severity\":").append(JsonUtil.quoteOrNull(a.severity)).append(",")
                    .append("\"category\":").append(JsonUtil.quoteOrNull(a.category)).append(",")
                    .append("\"title\":").append(JsonUtil.quoteOrNull(a.title)).append(",")
                    .append("\"description\":").append(JsonUtil.quoteOrNull(a.description)).append(",")
                    .append("\"linkUrl\":").append(JsonUtil.quoteOrNull(a.linkUrl)).append(",")
                    .append("\"approvedAtMillis\":").append(a.approvedAtMillis)
                    .append("}");
        }
        json.append("]");

        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
