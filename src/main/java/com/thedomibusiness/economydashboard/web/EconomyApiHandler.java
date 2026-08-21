package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.economy.EconomySnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class EconomyApiHandler implements HttpHandler {

    private final Supplier<EconomySnapshot> snapshotSupplier;

    public EconomyApiHandler(Supplier<EconomySnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        EconomySnapshot snapshot = snapshotSupplier.get();
        byte[] body = toJson(snapshot).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String toJson(EconomySnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"generatedAtMillis\":").append(snapshot.generatedAtMillis).append(",");
        sb.append("\"totalMoney\":").append(format(snapshot.totalMoney)).append(",");
        sb.append("\"playerCount\":").append(snapshot.playerCount).append(",");

        sb.append("\"topBalances\":[");
        for (int i = 0; i < snapshot.topBalances.size(); i++) {
            EconomySnapshot.PlayerBalance b = snapshot.topBalances.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escape(b.name)).append("\",\"balance\":").append(format(b.balance)).append("}");
        }
        sb.append("],");

        sb.append("\"allBalances\":[");
        int allLimit = Math.min(snapshot.allBalances.size(), 2000);
        for (int i = 0; i < allLimit; i++) {
            EconomySnapshot.PlayerBalance b = snapshot.allBalances.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escape(b.name)).append("\",\"balance\":").append(format(b.balance)).append("}");
        }
        sb.append("],");

        sb.append("\"distribution\":{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : snapshot.distribution.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(entry.getKey())).append("\":").append(entry.getValue());
        }
        sb.append("}");

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
