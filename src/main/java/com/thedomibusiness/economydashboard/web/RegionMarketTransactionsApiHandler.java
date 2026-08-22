package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.filter.TransactionFilter;
import com.thedomibusiness.economydashboard.regionmarket.RegionMarketService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** JSON counterpart to {@link RegionMarketCsvExportHandlers.Transactions} for the live table preview. */
public class RegionMarketTransactionsApiHandler implements HttpHandler {

    private static final int MAX_PREVIEW_LIMIT = 250;

    private final RegionMarketService service;

    public RegionMarketTransactionsApiHandler(RegionMarketService service) {
        this.service = service;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
        TransactionFilter filter = TransactionFilter.fromQuery(query, "region");
        filter.limit = Math.min(filter.limit, MAX_PREVIEW_LIMIT);

        String json = service != null ? service.queryTransactionsJson(filter) : "[]";
        byte[] body = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
