package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.filter.TransactionFilter;
import com.thedomibusiness.economydashboard.traders.DtlTradersLogCollector;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * JSON counterpart to {@link TraderTransactionsCsvExportHandler} for the live, filtered
 * on-page table preview - same filters, but capped to a small page instead of the CSV
 * export's much higher (up to 50000) limit.
 */
public class TraderTransactionsApiHandler implements HttpHandler {

    private static final int MAX_PREVIEW_LIMIT = 250;

    private final DtlTradersLogCollector tradersCollector;

    public TraderTransactionsApiHandler(DtlTradersLogCollector tradersCollector) {
        this.tradersCollector = tradersCollector;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
        TransactionFilter filter = TransactionFilter.fromQuery(query, "shop");
        filter.limit = Math.min(filter.limit, MAX_PREVIEW_LIMIT);

        String json = tradersCollector != null ? tradersCollector.queryTransactionsJson(filter) : "[]";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
