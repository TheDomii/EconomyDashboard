package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.economy.EconomyHistorySnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class EconomyHistoryApiHandler implements HttpHandler {

    private final Supplier<EconomyHistorySnapshot> snapshotSupplier;

    public EconomyHistoryApiHandler(Supplier<EconomyHistorySnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        EconomyHistorySnapshot snapshot = snapshotSupplier.get();
        byte[] body = toJson(snapshot).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String toJson(EconomyHistorySnapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"generatedAtMillis\":").append(s.generatedAtMillis).append(",\"dailyPoints\":[");
        for (int i = 0; i < s.dailyPoints.size(); i++) {
            EconomyHistorySnapshot.DailyMoneyPoint p = s.dailyPoints.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"day\":").append(JsonUtil.quoteOrNull(p.day)).append(",")
                    .append("\"avgMoney\":").append(JsonUtil.money(p.avgMoney)).append(",")
                    .append("\"maxPlayers\":").append(p.maxPlayers).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
}
