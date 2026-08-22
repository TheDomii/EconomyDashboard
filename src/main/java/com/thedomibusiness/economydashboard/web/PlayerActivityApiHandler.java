package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.presence.PlayerActivitySnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

public class PlayerActivityApiHandler implements HttpHandler {

    private final Supplier<PlayerActivitySnapshot> snapshotSupplier;

    public PlayerActivityApiHandler(Supplier<PlayerActivitySnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        PlayerActivitySnapshot snapshot = snapshotSupplier.get();
        byte[] body = toJson(snapshot).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String toJson(PlayerActivitySnapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"generatedAtMillis\":").append(s.generatedAtMillis).append(",")
                .append("\"currentOnline\":").append(s.currentOnline).append(",")
                .append("\"peakOnline\":").append(s.peakOnline).append(",")
                .append("\"peakOnlineAtMillis\":").append(s.peakOnlineAtMillis).append(",");

        sb.append("\"dailyCounts\":[");
        for (int i = 0; i < s.dailyCounts.size(); i++) {
            PlayerActivitySnapshot.DailyCount d = s.dailyCounts.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"day\":").append(JsonUtil.quoteOrNull(d.day)).append(",\"uniquePlayers\":").append(d.uniquePlayers).append("}");
        }
        sb.append("],");

        sb.append("\"hourlyPattern\":[");
        for (int i = 0; i < s.hourlyPattern.size(); i++) {
            PlayerActivitySnapshot.HourlyAverage h = s.hourlyPattern.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"hour\":").append(h.hour).append(",\"avgOnline\":").append(JsonUtil.money(h.avgOnline)).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
}
