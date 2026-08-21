package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.traders.TraderSnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Supplier;

public class TraderApiHandler implements HttpHandler {

    private final Supplier<TraderSnapshot> snapshotSupplier;

    public TraderApiHandler(Supplier<TraderSnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        TraderSnapshot snapshot = snapshotSupplier.get();
        byte[] body = toJson(snapshot).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String toJson(TraderSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"generatedAtMillis\":").append(snapshot.generatedAtMillis).append(",");
        sb.append("\"totalTransactions\":").append(snapshot.totalTransactions).append(",");
        sb.append("\"totalRevenue\":").append(format(snapshot.totalRevenue)).append(",");
        sb.append("\"totalPayouts\":").append(format(snapshot.totalPayouts)).append(",");
        sb.append("\"totalNet\":").append(format(snapshot.totalNet())).append(",");
        sb.append("\"totalTradeVolume\":").append(format(snapshot.totalTradeVolume)).append(",");

        sb.append("\"topShops\":[");
        for (int i = 0; i < snapshot.topShops.size(); i++) {
            TraderSnapshot.ShopStats s = snapshot.topShops.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escape(s.name)).append("\",")
              .append("\"transactions\":").append(s.transactions).append(",")
              .append("\"revenue\":").append(format(s.revenue)).append(",")
              .append("\"payouts\":").append(format(s.payouts)).append(",")
              .append("\"net\":").append(format(s.net())).append("}");
        }
        sb.append("],");

        sb.append("\"topItems\":[");
        for (int i = 0; i < snapshot.topItems.size(); i++) {
            TraderSnapshot.ItemStats it = snapshot.topItems.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escape(it.name)).append("\",")
              .append("\"boughtQty\":").append(it.boughtQty).append(",")
              .append("\"soldQty\":").append(it.soldQty).append("}");
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
