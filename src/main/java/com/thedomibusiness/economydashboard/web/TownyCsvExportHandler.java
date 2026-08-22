package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.towny.TownySnapshot;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class TownyCsvExportHandler implements HttpHandler {

    private final Supplier<TownySnapshot> snapshotSupplier;

    public TownyCsvExportHandler(Supplier<TownySnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
        String nameFilter = query.getOrDefault("name", "").toLowerCase(Locale.ROOT);
        String nationFilter = query.getOrDefault("nation", "").toLowerCase(Locale.ROOT);
        Double minBalance = parseDouble(query.get("minBalance"));

        TownySnapshot snapshot = snapshotSupplier.get();
        CsvBuilder csv = new CsvBuilder();
        csv.header("town", "nation", "balance", "plots");
        for (TownySnapshot.TownStats t : snapshot.allTowns) {
            if (!nameFilter.isEmpty() && !t.name.toLowerCase(Locale.ROOT).contains(nameFilter)) {
                continue;
            }
            if (!nationFilter.isEmpty() && !(t.nation != null ? t.nation.toLowerCase(Locale.ROOT) : "").contains(nationFilter)) {
                continue;
            }
            if (minBalance != null && t.balance < minBalance) {
                continue;
            }
            csv.row(t.name, t.nation, CsvBuilder.formatMoney(t.balance), t.plots);
        }

        HttpUtil.sendCsv(exchange, "staedte.csv", csv.build());
    }

    private Double parseDouble(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
