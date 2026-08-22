package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.anomaly.Anomaly;
import com.thedomibusiness.economydashboard.anomaly.AnomalyReport;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class AnomalyApiHandler implements HttpHandler {

    private final Supplier<AnomalyReport> reportSupplier;

    public AnomalyApiHandler(Supplier<AnomalyReport> reportSupplier) {
        this.reportSupplier = reportSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        AnomalyReport report = reportSupplier.get();
        byte[] body = toJson(report).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String toJson(AnomalyReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"generatedAtMillis\":").append(report.generatedAtMillis).append(",");
        sb.append("\"anomalies\":[");
        for (int i = 0; i < report.anomalies.size(); i++) {
            Anomaly a = report.anomalies.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"severity\":").append(JsonUtil.quoteOrNull(a.severity.name())).append(",")
                    .append("\"category\":").append(JsonUtil.quoteOrNull(a.category)).append(",")
                    .append("\"title\":").append(JsonUtil.quoteOrNull(a.title)).append(",")
                    .append("\"description\":").append(JsonUtil.quoteOrNull(a.description)).append(",")
                    .append("\"linkUrl\":").append(JsonUtil.quoteOrNull(a.linkUrl)).append(",")
                    .append("\"key\":").append(JsonUtil.quoteOrNull(a.key))
                    .append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
}
