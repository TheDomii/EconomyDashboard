package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.traders.ShopPriceEntry;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

public class TraderPricesCsvExportHandler implements HttpHandler {

    private final Supplier<List<ShopPriceEntry>> pricesSupplier;

    public TraderPricesCsvExportHandler(Supplier<List<ShopPriceEntry>> pricesSupplier) {
        this.pricesSupplier = pricesSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
        String shopFilter = query.getOrDefault("shop", "").toLowerCase(Locale.ROOT);
        String itemFilter = query.getOrDefault("item", "").toLowerCase(Locale.ROOT);

        CsvBuilder csv = new CsvBuilder();
        csv.header("shop", "page", "item", "buy_price", "sell_price");
        for (ShopPriceEntry p : pricesSupplier.get()) {
            if (!shopFilter.isEmpty() && !p.shop.toLowerCase(Locale.ROOT).contains(shopFilter)) {
                continue;
            }
            if (!itemFilter.isEmpty() && !p.itemName.toLowerCase(Locale.ROOT).contains(itemFilter)) {
                continue;
            }
            csv.row(p.shop, p.page, p.itemName,
                    p.buyPrice != null ? CsvBuilder.formatMoney(p.buyPrice) : "",
                    p.sellPrice != null ? CsvBuilder.formatMoney(p.sellPrice) : "");
        }

        HttpUtil.sendCsv(exchange, "preisliste.csv", csv.build());
    }
}
