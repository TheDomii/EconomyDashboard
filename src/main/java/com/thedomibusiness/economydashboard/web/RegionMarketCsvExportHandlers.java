package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.filter.TransactionFilter;
import com.thedomibusiness.economydashboard.regionmarket.RegionMarketService;

import java.io.IOException;
import java.util.Map;

/** Two small handlers for AdvancedRegionMarket's registry and transaction CSV exports. */
public class RegionMarketCsvExportHandlers {

    public static class Registry implements HttpHandler {
        private final RegionMarketService service;

        public Registry(RegionMarketService service) {
            this.service = service;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
            Boolean sold = parseBoolean(query.get("sold"));
            String csv = service != null ? service.exportRegionsCsv(query.get("owner"), query.get("world"), sold) : "";
            HttpUtil.sendCsv(exchange, "regionmarket.csv", csv);
        }

        private Boolean parseBoolean(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            if ("true".equalsIgnoreCase(raw.trim())) return Boolean.TRUE;
            if ("false".equalsIgnoreCase(raw.trim())) return Boolean.FALSE;
            return null;
        }
    }

    public static class Transactions implements HttpHandler {
        private final RegionMarketService service;

        public Transactions(RegionMarketService service) {
            this.service = service;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
            TransactionFilter filter = TransactionFilter.fromQuery(query, "region");
            String csv = service != null ? service.exportTransactionsCsv(filter) : "";
            HttpUtil.sendCsv(exchange, "regionmarket-transaktionen.csv", csv);
        }
    }
}
