package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.towny.TownySnapshot;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class TownyNationsCsvExportHandler implements HttpHandler {

    private final Supplier<TownySnapshot> snapshotSupplier;

    public TownyNationsCsvExportHandler(Supplier<TownySnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
        String nameFilter = query.getOrDefault("name", "").toLowerCase(Locale.ROOT);

        TownySnapshot snapshot = snapshotSupplier.get();
        CsvBuilder csv = new CsvBuilder();
        csv.header("nation", "capital", "town_count", "resident_count", "total_balance");
        for (TownySnapshot.NationStats n : snapshot.nations) {
            if (!nameFilter.isEmpty() && !n.name.toLowerCase(Locale.ROOT).contains(nameFilter)) {
                continue;
            }
            csv.row(n.name, n.capital, n.townCount, n.residentCount, CsvBuilder.formatMoney(n.totalBalance));
        }

        HttpUtil.sendCsv(exchange, "nationen.csv", csv.build());
    }
}
