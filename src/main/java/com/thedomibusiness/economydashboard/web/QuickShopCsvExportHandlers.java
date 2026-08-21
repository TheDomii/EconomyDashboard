package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.filter.TransactionFilter;
import com.thedomibusiness.economydashboard.quickshop.QuickShopService;

import java.io.IOException;
import java.util.Map;

/** Two small handlers for QuickShop's registry and transaction CSV exports. */
public class QuickShopCsvExportHandlers {

    public static class Registry implements HttpHandler {
        private final QuickShopService service;

        public Registry(QuickShopService service) {
            this.service = service;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
            String csv = service != null ? service.exportShopsCsv(query.get("owner"), query.get("item")) : "";
            HttpUtil.sendCsv(exchange, "quickshops.csv", csv);
        }
    }

    public static class Transactions implements HttpHandler {
        private final QuickShopService service;

        public Transactions(QuickShopService service) {
            this.service = service;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
            TransactionFilter filter = TransactionFilter.fromQuery(query, "owner");
            String csv = service != null ? service.exportTransactionsCsv(filter) : "";
            HttpUtil.sendCsv(exchange, "quickshop-transaktionen.csv", csv);
        }
    }
}
