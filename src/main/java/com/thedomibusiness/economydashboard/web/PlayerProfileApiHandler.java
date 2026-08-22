package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.economy.EconomySnapshot;
import com.thedomibusiness.economydashboard.filter.TransactionFilter;
import com.thedomibusiness.economydashboard.quickshop.QuickShopService;
import com.thedomibusiness.economydashboard.regionmarket.RegionMarketService;
import com.thedomibusiness.economydashboard.towny.TownyCollector;
import com.thedomibusiness.economydashboard.towny.TownyResidentSummary;
import com.thedomibusiness.economydashboard.traders.DtlTradersLogCollector;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Combines a player's data across every module into one profile - the cross-module link the
 * per-table views can't show on their own (see EconomyDashboardPlugin/index for the "why").
 * Towny's own API can only be touched from the main thread (it walks another plugin's live
 * objects, same constraint as TownyCollector#collect()), so that one piece is fetched via
 * Bukkit's scheduler and the HTTP handler thread blocks briefly waiting for it - the other
 * modules are all plain SQLite reads and stay on the handler thread.
 */
public class PlayerProfileApiHandler implements HttpHandler {

    private final Plugin plugin;
    private final Supplier<EconomySnapshot> economySnapshotSupplier;
    private final DtlTradersLogCollector tradersCollector;
    private final QuickShopService quickShopService;
    private final RegionMarketService regionMarketService;
    private final TownyCollector townyCollector;

    public PlayerProfileApiHandler(Plugin plugin, Supplier<EconomySnapshot> economySnapshotSupplier,
                                    DtlTradersLogCollector tradersCollector, QuickShopService quickShopService,
                                    RegionMarketService regionMarketService, TownyCollector townyCollector) {
        this.plugin = plugin;
        this.economySnapshotSupplier = economySnapshotSupplier;
        this.tradersCollector = tradersCollector;
        this.quickShopService = quickShopService;
        this.regionMarketService = regionMarketService;
        this.townyCollector = townyCollector;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Map<String, String> query = HttpUtil.parseQuery(exchange.getRequestURI().getRawQuery());
        String name = query.get("name");
        if (name == null || name.trim().isEmpty()) {
            byte[] body = "{\"error\":\"name required\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
            return;
        }
        name = name.trim();

        StringBuilder json = new StringBuilder("{");
        json.append("\"name\":").append(JsonUtil.quoteOrNull(name)).append(",");

        json.append("\"balance\":").append(findBalance(name)).append(",");

        TransactionFilter recentFilter = new TransactionFilter();
        recentFilter.player = name;
        recentFilter.limit = 20;

        json.append("\"traders\":");
        if (tradersCollector != null) {
            json.append("{\"summary\":").append(tradersCollector.playerSummaryJson(name)).append(",")
                    .append("\"recent\":").append(tradersCollector.queryTransactionsJson(recentFilter)).append("}");
        } else {
            json.append("null");
        }
        json.append(",");

        json.append("\"quickshop\":");
        if (quickShopService != null) {
            json.append("{\"summary\":").append(quickShopService.playerSummaryJson(name)).append(",")
                    .append("\"recent\":").append(quickShopService.queryTransactionsJson(recentFilter)).append("}");
        } else {
            json.append("null");
        }
        json.append(",");

        json.append("\"regionmarket\":");
        if (regionMarketService != null) {
            json.append("{\"summary\":").append(regionMarketService.playerSummaryJson(name)).append(",")
                    .append("\"recent\":").append(regionMarketService.queryTransactionsJson(recentFilter)).append("}");
        } else {
            json.append("null");
        }
        json.append(",");

        json.append("\"towny\":").append(residentJson(name));
        json.append("}");

        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String findBalance(String name) {
        for (EconomySnapshot.PlayerBalance b : economySnapshotSupplier.get().allBalances) {
            if (b.name != null && b.name.equalsIgnoreCase(name)) {
                return JsonUtil.money(b.balance);
            }
        }
        return "null";
    }

    private String residentJson(String name) {
        if (townyCollector == null) {
            return "null";
        }
        try {
            Future<TownyResidentSummary> future = plugin.getServer().getScheduler()
                    .callSyncMethod(plugin, () -> townyCollector.lookupResident(name));
            TownyResidentSummary resident = future.get(5, TimeUnit.SECONDS);
            if (resident == null) {
                return "null";
            }
            return "{\"town\":" + JsonUtil.quoteOrNull(resident.townName) + ","
                    + "\"nation\":" + JsonUtil.quoteOrNull(resident.nationName) + ","
                    + "\"joinedTownAt\":" + resident.joinedTownAtMillis + ","
                    + "\"registered\":" + resident.registeredMillis + ","
                    + "\"ownedPlots\":" + resident.ownedPlots + "}";
        } catch (TimeoutException e) {
            return "null";
        } catch (Exception e) {
            plugin.getLogger().warning("Towny-Spielerprofil fehlgeschlagen: " + e.getMessage());
            return "null";
        }
    }
}
