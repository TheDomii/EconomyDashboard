package com.thedomibusiness.economydashboard;

import com.thedomibusiness.economydashboard.auth.LoginConfig;
import com.thedomibusiness.economydashboard.chestshop.ChestShopService;
import com.thedomibusiness.economydashboard.chestshop.ChestShopSnapshot;
import com.thedomibusiness.economydashboard.economy.EconomyCollector;
import com.thedomibusiness.economydashboard.economy.EconomySnapshot;
import com.thedomibusiness.economydashboard.quickshop.QuickShopService;
import com.thedomibusiness.economydashboard.quickshop.QuickShopSnapshot;
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
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class EconomyDashboardPlugin extends JavaPlugin {

    private Economy economy;
    private EconomyCollector economyCollector;
    private DtlTradersLogCollector tradersCollector;
    private ShopPriceCollector priceCollector;
    private TownyCollector townyCollector;
    private ChestShopService chestShopService;
    private QuickShopService quickShopService;
    private DashboardHttpServer httpServer;
    private volatile EconomySnapshot latestSnapshot = EconomySnapshot.empty();
    private volatile TraderSnapshot latestTraderSnapshot = TraderSnapshot.empty();
    private volatile List<ShopPriceEntry> latestPrices = Collections.emptyList();
    private volatile TownySnapshot latestTownySnapshot = TownySnapshot.empty();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Kein Vault-Economy-Provider gefunden (z.B. TheNewEconomy). Plugin wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        boolean debug = getConfig().getBoolean("debug", false);
        boolean moduleEconomy = getConfig().getBoolean("modules.economy", true);
        boolean moduleTraders = getConfig().getBoolean("modules.traders", true);
        boolean moduleTowny = getConfig().getBoolean("modules.towny", true);
        boolean moduleChestShop = getConfig().getBoolean("modules.chestshop", true);
        boolean moduleQuickShop = getConfig().getBoolean("modules.quickshop", true);

        long refreshTicks = getConfig().getLong("refresh-interval-seconds", 60) * 20L;

        if (moduleEconomy) {
            this.economyCollector = new EconomyCollector(this, economy);
            getServer().getScheduler().runTaskTimer(this, () -> {
                latestSnapshot = economyCollector.collect();
            }, 20L, refreshTicks);
        } else {
            getLogger().info("Modul 'economy' ist in der config.yml deaktiviert.");
        }

        if (moduleTraders) {
            this.priceCollector = new ShopPriceCollector(this);
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                latestPrices = priceCollector.collect();
            }, 40L, refreshTicks);

            try {
                this.tradersCollector = new DtlTradersLogCollector(this);
                getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                    tradersCollector.update();
                    latestTraderSnapshot = tradersCollector.snapshotNow();
                }, 40L, refreshTicks);
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Konnte die Haendler-Datenbank nicht initialisieren - Haendler-Uebersicht bleibt leer.", e);
            }
        } else {
            getLogger().info("Modul 'traders' ist in der config.yml deaktiviert.");
        }

        if (moduleTowny && getServer().getPluginManager().isPluginEnabled("Towny")) {
            this.townyCollector = new TownyCollector();
            getServer().getScheduler().runTaskTimer(this, () -> {
                latestTownySnapshot = townyCollector.collect();
            }, 20L, refreshTicks);
            getLogger().info("Towny gefunden - Stadt-Uebersicht ist aktiv.");
        } else if (!moduleTowny) {
            getLogger().info("Modul 'towny' ist in der config.yml deaktiviert.");
        } else {
            getLogger().info("Towny nicht gefunden - Stadt-Uebersicht bleibt leer.");
        }

        if (moduleChestShop && getServer().getPluginManager().isPluginEnabled("ChestShop")) {
            try {
                this.chestShopService = new ChestShopService(this, debug);
                getLogger().info("ChestShop gefunden - ChestShop-Uebersicht ist aktiv.");
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Konnte die ChestShop-Datenbank nicht initialisieren - ChestShop-Uebersicht bleibt leer.", e);
            }
        } else if (!moduleChestShop) {
            getLogger().info("Modul 'chestshop' ist in der config.yml deaktiviert.");
        } else {
            getLogger().info("ChestShop nicht gefunden - ChestShop-Uebersicht bleibt leer.");
        }

        if (moduleQuickShop && getServer().getPluginManager().isPluginEnabled("QuickShop-Hikari")) {
            try {
                this.quickShopService = new QuickShopService(this);
                getServer().getScheduler().runTaskTimer(this, () -> {
                    quickShopService.refreshRegistry();
                }, 20L, refreshTicks);
                getLogger().info("QuickShop-Hikari gefunden - QuickShop-Uebersicht ist aktiv.");
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Konnte die QuickShop-Datenbank nicht initialisieren - QuickShop-Uebersicht bleibt leer.", e);
            }
        } else if (!moduleQuickShop) {
            getLogger().info("Modul 'quickshop' ist in der config.yml deaktiviert.");
        } else {
            getLogger().info("QuickShop-Hikari nicht gefunden - QuickShop-Uebersicht bleibt leer.");
        }

        SearchService searchService = new SearchService(this, economy, tradersCollector, () -> latestPrices,
                chestShopService, quickShopService);

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
                    () -> latestTownySnapshot, this::latestChestShopSnapshot, this::latestQuickShopSnapshot,
                    searchService, loginConfig);
            this.httpServer.start();
            getLogger().info("Dashboard laeuft auf http://" + bindAddress + ":" + port + "/"
                    + (loginConfig.enabled ? " (Login erforderlich)" : " (KEIN Login - ungeschuetzt)"));
        } catch (Exception e) {
            getLogger().severe("Konnte den Dashboard-Webserver nicht starten: " + e.getMessage());
        }
    }

    private ChestShopSnapshot latestChestShopSnapshot() {
        return chestShopService != null ? chestShopService.snapshotNow() : ChestShopSnapshot.empty();
    }

    private QuickShopSnapshot latestQuickShopSnapshot() {
        return quickShopService != null ? quickShopService.snapshotNow() : QuickShopSnapshot.empty();
    }

    @Override
    public void onDisable() {
        if (httpServer != null) {
            httpServer.stop();
        }
        if (tradersCollector != null) {
            tradersCollector.close();
        }
        if (chestShopService != null) {
            chestShopService.close();
        }
        if (quickShopService != null) {
            quickShopService.close();
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
