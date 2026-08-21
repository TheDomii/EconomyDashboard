package com.thedomibusiness.economydashboard;

import com.thedomibusiness.economydashboard.auth.LoginConfig;
import com.thedomibusiness.economydashboard.economy.EconomyCollector;
import com.thedomibusiness.economydashboard.economy.EconomySnapshot;
import com.thedomibusiness.economydashboard.quickshop.QuickShopService;
import com.thedomibusiness.economydashboard.quickshop.QuickShopSnapshot;
import com.thedomibusiness.economydashboard.regionmarket.RegionMarketService;
import com.thedomibusiness.economydashboard.regionmarket.RegionMarketSnapshot;
import com.thedomibusiness.economydashboard.search.SearchService;
import com.thedomibusiness.economydashboard.towny.TownyCollector;
import com.thedomibusiness.economydashboard.towny.TownySnapshot;
import com.thedomibusiness.economydashboard.traders.DtlTradersLogCollector;
import com.thedomibusiness.economydashboard.traders.ShopPriceCollector;
import com.thedomibusiness.economydashboard.traders.ShopPriceEntry;
import com.thedomibusiness.economydashboard.traders.TraderSnapshot;
import com.thedomibusiness.economydashboard.web.DashboardHttpServer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class EconomyDashboardPlugin extends JavaPlugin {

    private Economy economy;
    private EconomyCollector economyCollector;
    private DtlTradersLogCollector tradersCollector;
    private ShopPriceCollector priceCollector;
    private TownyCollector townyCollector;
    private QuickShopService quickShopService;
    private RegionMarketService regionMarketService;
    private DashboardHttpServer httpServer;
    private volatile EconomySnapshot latestSnapshot = EconomySnapshot.empty();
    private volatile TraderSnapshot latestTraderSnapshot = TraderSnapshot.empty();
    private volatile List<ShopPriceEntry> latestPrices = Collections.emptyList();
    private volatile TownySnapshot latestTownySnapshot = TownySnapshot.empty();

    /** Lines collected while modules start up, printed as one status block at the end. */
    private final List<String> statusLines = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Kein Vault-Economy-Provider gefunden (z.B. TheNewEconomy). Plugin wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        statusLines.add("economy-provider : aktiv (" + economy.getName() + " via Vault)");

        boolean moduleEconomy = getConfig().getBoolean("modules.economy", true);
        boolean moduleTraders = getConfig().getBoolean("modules.traders", true);
        boolean moduleTowny = getConfig().getBoolean("modules.towny", true);
        boolean moduleQuickShop = getConfig().getBoolean("modules.quickshop", true);
        boolean moduleRegionMarket = getConfig().getBoolean("modules.regionmarket", true);

        long refreshTicks = getConfig().getLong("refresh-interval-seconds", 60) * 20L;

        if (moduleEconomy) {
            this.economyCollector = new EconomyCollector(this, economy);
            AtomicBoolean firstRun = new AtomicBoolean(true);
            getServer().getScheduler().runTaskTimer(this, () -> {
                latestSnapshot = economyCollector.collect();
                if (firstRun.compareAndSet(true, false)) {
                    getLogger().info("[economy] Erste Daten erfasst: " + latestSnapshot.playerCount
                            + " Spieler, " + String.format("%.2f", latestSnapshot.totalMoney) + " im Umlauf.");
                }
            }, 20L, refreshTicks);
            statusLines.add("economy           : aktiv");
        } else {
            statusLines.add("economy           : deaktiviert (config.yml)");
        }

        if (moduleTraders) {
            this.priceCollector = new ShopPriceCollector(this);
            AtomicBoolean priceFirstRun = new AtomicBoolean(true);
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                latestPrices = priceCollector.collect();
                if (priceFirstRun.compareAndSet(true, false)) {
                    getLogger().info("[traders] Erste Preisliste erfasst: " + latestPrices.size() + " Eintraege.");
                }
            }, 40L, refreshTicks);

            try {
                this.tradersCollector = new DtlTradersLogCollector(this);
                AtomicBoolean txFirstRun = new AtomicBoolean(true);
                getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                    tradersCollector.update();
                    latestTraderSnapshot = tradersCollector.snapshotNow();
                    if (txFirstRun.compareAndSet(true, false)) {
                        getLogger().info("[traders] Erste Handelsdaten erfasst: " + latestTraderSnapshot.totalTransactions
                                + " Transaktionen in " + latestTraderSnapshot.topShops.size() + " Shops (Top-Liste).");
                    }
                }, 40L, refreshTicks);
                statusLines.add("traders (dtlTradersPlus) : aktiv");
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Konnte die Haendler-Datenbank nicht initialisieren - Haendler-Uebersicht bleibt leer.", e);
                statusLines.add("traders (dtlTradersPlus) : FEHLER (Datenbank konnte nicht initialisiert werden)");
            }
        } else {
            statusLines.add("traders (dtlTradersPlus) : deaktiviert (config.yml)");
        }

        if (moduleTowny && getServer().getPluginManager().isPluginEnabled("Towny")) {
            this.townyCollector = new TownyCollector();
            AtomicBoolean firstRun = new AtomicBoolean(true);
            getServer().getScheduler().runTaskTimer(this, () -> {
                latestTownySnapshot = townyCollector.collect();
                if (firstRun.compareAndSet(true, false)) {
                    getLogger().info("[towny] Erste Daten erfasst: " + latestTownySnapshot.totalTowns + " Staedte.");
                }
            }, 20L, refreshTicks);
            statusLines.add("towny             : aktiv (Towny gefunden)");
        } else if (!moduleTowny) {
            statusLines.add("towny             : deaktiviert (config.yml)");
        } else {
            statusLines.add("towny             : inaktiv (Towny nicht gefunden)");
        }

        if (moduleQuickShop && getServer().getPluginManager().isPluginEnabled("QuickShop-Hikari")) {
            try {
                this.quickShopService = new QuickShopService(this);
                AtomicBoolean firstRun = new AtomicBoolean(true);
                getServer().getScheduler().runTaskTimer(this, () -> {
                    quickShopService.refreshRegistry();
                    if (firstRun.compareAndSet(true, false)) {
                        QuickShopSnapshot snap = quickShopService.snapshotNow();
                        getLogger().info("[quickshop] Erste Daten erfasst: " + snap.totalShops + " Shops.");
                    }
                }, 20L, refreshTicks);
                statusLines.add("quickshop         : aktiv (QuickShop-Hikari gefunden)");
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Konnte die QuickShop-Datenbank nicht initialisieren - QuickShop-Uebersicht bleibt leer.", e);
                statusLines.add("quickshop         : FEHLER (Datenbank konnte nicht initialisiert werden)");
            }
        } else if (!moduleQuickShop) {
            statusLines.add("quickshop         : deaktiviert (config.yml)");
        } else {
            statusLines.add("quickshop         : inaktiv (QuickShop-Hikari nicht gefunden)");
        }

        if (moduleRegionMarket && getServer().getPluginManager().isPluginEnabled("AdvancedRegionMarket")) {
            try {
                this.regionMarketService = new RegionMarketService(this);
                AtomicBoolean firstRun = new AtomicBoolean(true);
                getServer().getScheduler().runTaskTimer(this, () -> {
                    regionMarketService.refreshRegistry();
                    if (firstRun.compareAndSet(true, false)) {
                        RegionMarketSnapshot snap = regionMarketService.snapshotNow();
                        getLogger().info("[regionmarket] Erste Daten erfasst: " + snap.totalRegions
                                + " Regionen (" + snap.soldRegions + " verkauft).");
                    }
                }, 20L, refreshTicks);
                statusLines.add("regionmarket      : aktiv (AdvancedRegionMarket gefunden)");
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Konnte die AdvancedRegionMarket-Datenbank nicht initialisieren - Region-Uebersicht bleibt leer.", e);
                statusLines.add("regionmarket      : FEHLER (Datenbank konnte nicht initialisiert werden)");
            }
        } else if (!moduleRegionMarket) {
            statusLines.add("regionmarket      : deaktiviert (config.yml)");
        } else {
            statusLines.add("regionmarket      : inaktiv (AdvancedRegionMarket nicht gefunden)");
        }

        SearchService searchService = new SearchService(() -> latestSnapshot, tradersCollector, () -> latestPrices,
                quickShopService, regionMarketService);

        LoginConfig loginConfig = new LoginConfig(
                getConfig().getBoolean("login.enabled", true),
                getConfig().getString("login.username", "admin"),
                getConfig().getString("login.password", "changeme"),
                getConfig().getString("login.background-image", ""),
                getConfig().getLong("login.session-minutes", 720)
        );

        try {
            String bindAddress = getConfig().getString("web-server.bind-address", "0.0.0.0");
            int port = getConfig().getInt("web-server.port", 8080);
            this.httpServer = new DashboardHttpServer(this, bindAddress, port,
                    () -> latestSnapshot, () -> latestTraderSnapshot, () -> latestPrices,
                    () -> latestTownySnapshot, this::latestQuickShopSnapshot, this::latestRegionMarketSnapshot,
                    tradersCollector, quickShopService, regionMarketService,
                    searchService, loginConfig);
            this.httpServer.start();
            statusLines.add("web-dashboard     : http://" + bindAddress + ":" + port + "/"
                    + (loginConfig.enabled ? " (Login erforderlich)" : " (KEIN Login - ungeschuetzt!)"));
        } catch (Exception e) {
            getLogger().severe("Konnte den Dashboard-Webserver nicht starten: " + e.getMessage());
            statusLines.add("web-dashboard     : FEHLER (" + e.getMessage() + ")");
        }

        printStatusBlock();
    }

    private void printStatusBlock() {
        String divider = "============================================================";
        getLogger().info(divider);
        getLogger().info(" EconomyDashboard v" + getDescription().getVersion() + " - Status");
        for (String line : statusLines) {
            getLogger().info(" " + line);
        }
        getLogger().info(divider);
    }

    private QuickShopSnapshot latestQuickShopSnapshot() {
        return quickShopService != null ? quickShopService.snapshotNow() : QuickShopSnapshot.empty();
    }

    private RegionMarketSnapshot latestRegionMarketSnapshot() {
        return regionMarketService != null ? regionMarketService.snapshotNow() : RegionMarketSnapshot.empty();
    }

    @Override
    public void onDisable() {
        if (httpServer != null) {
            httpServer.stop();
        }
        if (tradersCollector != null) {
            tradersCollector.close();
        }
        if (quickShopService != null) {
            quickShopService.close();
        }
        if (regionMarketService != null) {
            regionMarketService.close();
        }
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        this.economy = rsp.getProvider();
        return economy != null;
    }
}
