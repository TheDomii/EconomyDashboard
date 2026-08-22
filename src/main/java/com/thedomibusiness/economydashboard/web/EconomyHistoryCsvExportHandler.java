package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.economy.EconomyHistorySnapshot;

import java.io.IOException;
import java.util.function.Supplier;

public class EconomyHistoryCsvExportHandler implements HttpHandler {

    private final Supplier<EconomyHistorySnapshot> snapshotSupplier;

    public EconomyHistoryCsvExportHandler(Supplier<EconomyHistorySnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        EconomyHistorySnapshot snapshot = snapshotSupplier.get();
        CsvBuilder csv = new CsvBuilder();
        csv.header("day", "avg_money", "max_players_online");
        for (EconomyHistorySnapshot.DailyMoneyPoint p : snapshot.dailyPoints) {
            csv.row(p.day, CsvBuilder.formatMoney(p.avgMoney), p.maxPlayers);
        }
        HttpUtil.sendCsv(exchange, "geldmenge-pro-tag.csv", csv.build());
    }
}
