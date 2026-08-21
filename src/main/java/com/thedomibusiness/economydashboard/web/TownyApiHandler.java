package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.towny.TownySnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Supplier;

public class TownyApiHandler implements HttpHandler {

    private final Supplier<TownySnapshot> snapshotSupplier;

    public TownyApiHandler(Supplier<TownySnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        TownySnapshot snapshot = snapshotSupplier.get();
        byte[] body = toJson(snapshot).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String toJson(TownySnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"generatedAtMillis\":").append(snapshot.generatedAtMillis).append(",");
        sb.append("\"totalTowns\":").append(snapshot.totalTowns).append(",");
        sb.append("\"totalTownBalance\":").append(format(snapshot.totalTownBalance)).append(",");
        sb.append("\"newTownsLast24h\":").append(snapshot.newTownsLast24h).append(",");
        sb.append("\"newTownsLast7d\":").append(snapshot.newTownsLast7d).append(",");
        sb.append("\"newPlotsLast24h\":").append(snapshot.newPlotsLast24h).append(",");
        sb.append("\"newPlotsLast7d\":").append(snapshot.newPlotsLast7d).append(",");

        sb.append("\"topTowns\":[");
        for (int i = 0; i < snapshot.topTowns.size(); i++) {
            TownySnapshot.TownStats t = snapshot.topTowns.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escape(t.name)).append("\",")
              .append("\"balance\":").append(format(t.balance)).append(",")
              .append("\"plots\":").append(t.plots).append(",")
              .append("\"nation\":").append(t.nation != null ? "\"" + escape(t.nation) + "\"" : "null")
              .append("}");
        }
        sb.append("],");

        sb.append("\"allTowns\":[");
        int allLimit = Math.min(snapshot.allTowns.size(), 2000);
        for (int i = 0; i < allLimit; i++) {
            TownySnapshot.TownStats t = snapshot.allTowns.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escape(t.name)).append("\",")
              .append("\"balance\":").append(format(t.balance)).append(",")
              .append("\"plots\":").append(t.plots).append(",")
              .append("\"nation\":").append(t.nation != null ? "\"" + escape(t.nation) + "\"" : "null")
              .append("}");
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
