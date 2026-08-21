package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.chestshop.ChestShopSnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Supplier;

public class ChestShopApiHandler implements HttpHandler {

    private final Supplier<ChestShopSnapshot> snapshotSupplier;

    public ChestShopApiHandler(Supplier<ChestShopSnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        ChestShopSnapshot snapshot = snapshotSupplier.get();
        byte[] body = toJson(snapshot).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String toJson(ChestShopSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"generatedAtMillis\":").append(snapshot.generatedAtMillis).append(",");
        sb.append("\"totalShops\":").append(snapshot.totalShops).append(",");
        sb.append("\"totalTransactions\":").append(snapshot.totalTransactions).append(",");
        sb.append("\"totalRevenue\":").append(format(snapshot.totalRevenue)).append(",");
        sb.append("\"totalPayouts\":").append(format(snapshot.totalPayouts)).append(",");

        sb.append("\"topOwners\":[");
        for (int i = 0; i < snapshot.topOwners.size(); i++) {
            ChestShopSnapshot.OwnerStats o = snapshot.topOwners.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"owner\":\"").append(escape(o.owner)).append("\",")
              .append("\"shopCount\":").append(o.shopCount).append(",")
              .append("\"transactions\":").append(o.transactions).append(",")
              .append("\"revenue\":").append(format(o.revenue)).append(",")
              .append("\"payouts\":").append(format(o.payouts)).append(",")
              .append("\"net\":").append(format(o.net())).append("}");
        }
        sb.append("],");

        sb.append("\"shops\":[");
        for (int i = 0; i < snapshot.shops.size(); i++) {
            ChestShopSnapshot.ShopListing s = snapshot.shops.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"owner\":\"").append(escape(nullToEmpty(s.owner))).append("\",")
              .append("\"item\":\"").append(escape(nullToEmpty(s.item))).append("\",")
              .append("\"quantity\":\"").append(escape(nullToEmpty(s.quantity))).append("\",")
              .append("\"buyPrice\":").append(s.buyPrice != null ? format(s.buyPrice) : "null").append(",")
              .append("\"sellPrice\":").append(s.sellPrice != null ? format(s.sellPrice) : "null").append(",")
              .append("\"location\":\"").append(escape(s.location)).append("\"}");
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
