package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.search.SearchResult;
import com.thedomibusiness.economydashboard.search.SearchService;
import com.thedomibusiness.economydashboard.traders.TraderSnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class SearchApiHandler implements HttpHandler {

    private final SearchService searchService;

    public SearchApiHandler(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String query = queryParam(exchange.getRequestURI().getRawQuery(), "q");
        byte[] body;
        if (query == null || query.trim().length() < 2) {
            body = "{\"error\":\"query too short (min. 2 characters)\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
            return;
        }

        SearchResult result = searchService.search(query.trim());
        body = toJson(result).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String queryParam(String rawQuery, String key) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            if (!k.equals(key)) {
                continue;
            }
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            try {
                return URLDecoder.decode(v, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return v;
            }
        }
        return null;
    }

    private String toJson(SearchResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        sb.append("\"players\":[");
        for (int i = 0; i < result.players.size(); i++) {
            SearchResult.PlayerResult p = result.players.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escape(p.name)).append("\",\"balance\":").append(format(p.balance)).append("}");
        }
        sb.append("],");

        sb.append("\"shops\":[");
        for (int i = 0; i < result.shops.size(); i++) {
            TraderSnapshot.ShopStats s = result.shops.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escape(s.name)).append("\",")
              .append("\"transactions\":").append(s.transactions).append(",")
              .append("\"revenue\":").append(format(s.revenue)).append(",")
              .append("\"payouts\":").append(format(s.payouts)).append(",")
              .append("\"net\":").append(format(s.net())).append("}");
        }
        sb.append("],");

        sb.append("\"items\":[");
        for (int i = 0; i < result.items.size(); i++) {
            SearchResult.ItemResult it = result.items.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escape(it.name)).append("\",")
              .append("\"boughtQty\":").append(it.boughtQty).append(",")
              .append("\"soldQty\":").append(it.soldQty).append(",")
              .append("\"buyPrice\":").append(it.buyPrice != null ? format(it.buyPrice) : "null").append(",")
              .append("\"sellPrice\":").append(it.sellPrice != null ? format(it.sellPrice) : "null").append("}");
        }
        sb.append("],");

        sb.append("\"chestShops\":[");
        for (int i = 0; i < result.chestShops.size(); i++) {
            com.thedomibusiness.economydashboard.chestshop.ChestShopSnapshot.ShopListing s = result.chestShops.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"owner\":\"").append(escape(nullToEmpty(s.owner))).append("\",")
              .append("\"item\":\"").append(escape(nullToEmpty(s.item))).append("\",")
              .append("\"buyPrice\":").append(s.buyPrice != null ? format(s.buyPrice) : "null").append(",")
              .append("\"sellPrice\":").append(s.sellPrice != null ? format(s.sellPrice) : "null").append("}");
        }
        sb.append("],");

        sb.append("\"quickShops\":[");
        for (int i = 0; i < result.quickShops.size(); i++) {
            com.thedomibusiness.economydashboard.quickshop.QuickShopSnapshot.ShopListing s = result.quickShops.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"owner\":\"").append(escape(nullToEmpty(s.owner))).append("\",")
              .append("\"item\":\"").append(escape(nullToEmpty(s.item))).append("\",")
              .append("\"price\":").append(format(s.price)).append(",")
              .append("\"shopBuys\":").append(s.shopBuys).append("}");
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
