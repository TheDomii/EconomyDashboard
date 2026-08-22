package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.presence.PlayerActivitySnapshot;

import java.io.IOException;
import java.util.function.Supplier;

public class PlayerActivityCsvExportHandler implements HttpHandler {

    private final Supplier<PlayerActivitySnapshot> snapshotSupplier;

    public PlayerActivityCsvExportHandler(Supplier<PlayerActivitySnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        PlayerActivitySnapshot snapshot = snapshotSupplier.get();
        CsvBuilder csv = new CsvBuilder();
        csv.header("day", "unique_players");
        for (PlayerActivitySnapshot.DailyCount d : snapshot.dailyCounts) {
            csv.row(d.day, d.uniquePlayers);
        }
        HttpUtil.sendCsv(exchange, "spieler-pro-tag.csv", csv.build());
    }
}
