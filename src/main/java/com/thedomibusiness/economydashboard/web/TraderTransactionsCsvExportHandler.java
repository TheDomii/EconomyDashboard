package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.filter.TransactionFilter;
import com.thedomibusiness.economydashboard.traders.DtlTradersLogCollector;

import java.io.IOException;
import java.util.Map;

public class TraderTransactionsCsvExportHandler implements HttpHandler {

    private final DtlTradersLogCollector tradersCollector;

    public TraderTransactionsCsvExportHandler(DtlTradersLogCollector tradersCollector) {
        this.tradersCollector = tradersCollector;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
        TransactionFilter filter = TransactionFilter.fromQuery(query, "shop");

        String csv = tradersCollector != null ? tradersCollector.exportTransactionsCsv(filter) : "";
        HttpUtil.sendCsv(exchange, "haendler-transaktionen.csv", csv);
    }
}
