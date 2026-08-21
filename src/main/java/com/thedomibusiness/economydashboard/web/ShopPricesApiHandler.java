package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.traders.ShopPriceEntry;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class ShopPricesApiHandler implements HttpHandler {

    private final Supplier<List<ShopPriceEntry>> pricesSupplier;

    public ShopPricesApiHandler(Supplier<List<ShopPriceEntry>> pricesSupplier) {
        this.pricesSupplier = pricesSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<ShopPriceEntry> prices = pricesSupplier.get();
        byte[] body = toJson(prices).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String toJson(List<ShopPriceEntry> prices) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < prices.size(); i++) {
            ShopPriceEntry p = prices.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"shop\":\"").append(escape(p.shop)).append("\",")
              .append("\"page\":\"").append(escape(p.page)).append("\",")
              .append("\"item\":\"").append(escape(p.itemName)).append("\",")
              .append("\"buyPrice\":").append(p.buyPrice != null ? format(p.buyPrice) : "null").append(",")
              .append("\"sellPrice\":").append(p.sellPrice != null ? format(p.sellPrice) : "null").append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
