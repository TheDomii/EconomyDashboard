package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.anomaly.AnomalyApprovalService;
import com.thedomibusiness.economydashboard.anomaly.ArchivedAnomaly;

import java.io.IOException;

public class AnomalyArchiveCsvExportHandler implements HttpHandler {

    private final AnomalyApprovalService approvalService;

    public AnomalyArchiveCsvExportHandler(AnomalyApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        CsvBuilder csv = new CsvBuilder();
        csv.header("approved_at", "severity", "category", "title", "description", "link");
        for (ArchivedAnomaly a : approvalService.listArchive()) {
            csv.row(CsvBuilder.formatTimestamp(a.approvedAtMillis), a.severity, a.category, a.title, a.description, a.linkUrl);
        }
        HttpUtil.sendCsv(exchange, "anomalie-archiv.csv", csv.build());
    }
}
