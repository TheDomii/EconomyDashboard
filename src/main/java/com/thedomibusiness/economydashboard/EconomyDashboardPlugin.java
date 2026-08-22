package com.thedomibusiness.economydashboard;

import com.thedomibusiness.economydashboard.activity.ActivityFeedService;
import com.thedomibusiness.economydashboard.anomaly.Anomaly;
import com.thedomibusiness.economydashboard.anomaly.AnomalyApprovalService;
import com.thedomibusiness.economydashboard.anomaly.AnomalyDetector;
import com.thedomibusiness.economydashboard.anomaly.AnomalyReport;
import com.thedomibusiness.economydashboard.anomaly.SellVolumeRow;
import com.thedomibusiness.economydashboard.auth.LoginConfig;
import com.thedomibusiness.economydashboard.command.EcoDashCommand;
import com.thedomibusiness.economydashboard.economy.EconomyCollector;
import com.thedomibusiness.economydashboard.economy.EconomyHistoryService;
import com.thedomibusiness.economydashboard.economy.EconomySnapshot;
import com.thedomibusiness.economydashboard.notify.DiscordWebhookNotifier;
import com.thedomibusiness.economydashboard.presence.PlayerPresenceService;
import com.thedomibusiness.economydashboard.quickshop.QuickShopService;
import com.thedomibusiness.economydashboard.quickshop.QuickShopSnapshot;
import com.thedomibusiness.economydashboard.regionmarket.RegionMarketService;
import com.thedomibusiness.economydashboard.regionmarket.RegionMarketSnapshot;
import com.thedomibusiness.economydashboard.search.SearchService;
import com.thedomibusiness.economydashboard.towny.TownyActivityService;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class EconomyDashboardPlugin extends JavaPlugin {

    private Economy economy;
    private EconomyCollector economyCollector;
    private EconomyHistoryService economyHistoryService;
    private DtlTradersLogCollector tradersCollector;
    private ShopPriceCollector priceCollector;
    private TownyCollector townyCollector;
    private TownyActivityService townyActivityService;
    private QuickShopService quickShopService;
    private RegionMarketService regionMarketService;
    private PlayerPresenceService playerPresenceService;
    private AnomalyApprovalService anomalyApprovalService;
    private ActivityFeedService activityFeedService;
    private SearchService searchService;
    private DashboardHttpServer httpServer;
    private volatile EconomySnapshot latestSnapshot = EconomySnapshot.empty();
    private volatile TraderSnapshot latestTraderSnapshot = TraderSnapshot.empty();
    private volatile List<ShopPriceEntry> latestPrices = Collections.emptyList();
    private volatile TownySnapshot latestTownySnapshot = TownySnapshot.empty();
    private volatile AnomalyReport latestAnomalyReport = AnomalyReport.empty();

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
        boolean modulePlayerActivity = getConfig().getBoolean("modules.player-activity", true);

        long refreshTicks = getConfig().getLong("refresh-interval-seconds", 60) * 20L;

        if (moduleEconomy) {
            this.economyCollector = new EconomyCollector(this, economy);
            try {
                this.economyHistoryService = new EconomyHistoryService(this);
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Konnte die Geldmengen-Verlaufs-Datenbank nicht initialisieren.", e);
            }
            AtomicBoolean firstRun = new AtomicBoolean(true);
            getServer().getScheduler().runTaskTimer(this, () -> {
                latestSnapshot = economyCollector.collect();
                if (economyHistoryService != null) {
                    economyHistoryService.recordSample(latestSnapshot.totalMoney, latestSnapshot.playerCount);
                }
                if (firstRun.compareAndSet(true, false)) {
                    getLogger().info("[economy] Erste Daten erfasst: " + latestSnapshot.playerCount
                            + " Spieler, " + String.format("%.2f", latestSnapshot.totalMoney) + " im Umlauf.");
                }
            }, 20L, refreshTicks);
            statusLines.add("economy           : aktiv (Geldmengen-Verlauf wird ab jetzt mitgeschrieben)");
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
            this.townyActivityService = new TownyActivityService(this);
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
                // Registry refresh is a direct DatabaseHelper#listShops() read (see
                // QuickShopRegistryCollector) - no Bukkit API calls, so it's safe off-thread.
                getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
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

        if (modulePlayerActivity) {
            try {
                this.playerPresenceService = new PlayerPresenceService(this);
                // Must run sync - Bukkit.getOnlinePlayers() is a main-thread-only API.
                getServer().getScheduler().runTaskTimer(this, playerPresenceService::sampleOnlineCount, 20L, refreshTicks);
                statusLines.add("player-activity   : aktiv (Aufzeichnung startet jetzt, keine rueckwirkenden Daten)");
            } catch (SQLException e) {
                getLogger().log(Level.SEVERE, "Konnte die Spieler-Aktivitaets-Datenbank nicht initialisieren.", e);
                statusLines.add("player-activity   : FEHLER (Datenbank konnte nicht initialisiert werden)");
            }
        } else {
            statusLines.add("player-activity   : deaktiviert (config.yml)");
        }

        this.activityFeedService = new ActivityFeedService(tradersCollector, quickShopService, regionMarketService, townyActivityService);

        try {
            this.anomalyApprovalService = new AnomalyApprovalService(this);
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Konnte die Anomalie-Freigabe-Datenbank nicht initialisieren.", e);
        }

        DiscordWebhookNotifier discordNotifier = new DiscordWebhookNotifier(this, getConfig().getString("webhook.discord-url", ""));
        String dashboardPublicUrl = getConfig().getString("web-server.public-url", "");

        if (moduleTraders || moduleQuickShop || moduleTowny) {
            AnomalyDetector anomalyDetector = new AnomalyDetector();
            // Scans the full transaction history via SQL GROUP BY - heavier than the other
            // collectors, so it runs far less often (every 5 minutes) and always off-thread.
            long anomalyIntervalTicks = Math.max(refreshTicks * 5, 20L * 60 * 5);
            AtomicBoolean firstRun = new AtomicBoolean(true);
            // Seeded (not notified) on the first run so a plugin/server restart doesn't re-blast
            // Discord with every already-known anomaly - only genuinely new ones after that.
            Set<String> notifiedHighKeys = new java.util.HashSet<>();
            getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                List<SellVolumeRow> sellVolume = new ArrayList<>();
                if (tradersCollector != null) {
                    sellVolume.addAll(tradersCollector.sellVolumeByItemAndPlayer());
                }
                if (quickShopService != null) {
                    sellVolume.addAll(quickShopService.sellVolumeByItemAndPlayer());
                }
                List<Anomaly> anomalies = anomalyDetector.detect(sellVolume, latestTownySnapshot);
                if (anomalyApprovalService != null) {
                    Set<String> approved = anomalyApprovalService.approvedKeys();
                    anomalies = anomalies.stream().filter(a -> !approved.contains(a.key)).collect(Collectors.toList());
                }
                latestAnomalyReport = new AnomalyReport(System.currentTimeMillis(), anomalies);

                boolean isFirstRun = firstRun.compareAndSet(true, false);
                if (isFirstRun) {
                    getLogger().info("[anomalies] Erste Analyse abgeschlossen: " + anomalies.size() + " Auffaelligkeiten gefunden.");
                }
                if (discordNotifier.isEnabled()) {
                    for (Anomaly a : anomalies) {
                        if (a.severity == Anomaly.Severity.HIGH && notifiedHighKeys.add(a.key) && !isFirstRun) {
                            discordNotifier.sendHighSeverityAlert(a.title, a.description, a.linkUrl, dashboardPublicUrl);
                        }
                    }
                }
            }, 200L, anomalyIntervalTicks);
            statusLines.add("anomaly-detection : aktiv (alle " + (anomalyIntervalTicks / 20L) + "s)"
                    + (discordNotifier.isEnabled() ? ", Discord-Alarm aktiv" : ""));
        } else {
            statusLines.add("anomaly-detection : inaktiv (keine relevanten Module aktiv)");
        }

        this.searchService = new SearchService(() -> latestSnapshot, tradersCollector, () -> latestPrices,
                quickShopService, regionMarketService);

        statusLines.add(startHttpServer());
        printStatusBlock();

        EcoDashCommand commandExecutor = new EcoDashCommand(this);
        getCommand("ecodash").setExecutor(commandExecutor);
        getCommand("ecodash").setTabCompleter(commandExecutor);
    }

    /** (Re-)starts the embedded web server using the current config.yml values - reused by
     *  onEnable() and by "/ecodash reload". Reuses the already-constructed collectors/services
     *  as-is: module on/off toggles and refresh-interval changes still need a full server
     *  restart, only web-server/login settings are actually reloadable this way. Returns one
     *  status line describing the outcome (not added to statusLines here, so a reload doesn't
     *  pile up duplicate lines in "/ecodash status"). */
    private String startHttpServer() {
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
                    () -> latestAnomalyReport, this::latestPlayerActivitySnapshot, this::latestEconomyHistorySnapshot,
                    tradersCollector, quickShopService, regionMarketService, townyCollector,
                    searchService, activityFeedService, anomalyApprovalService, loginConfig);
            this.httpServer.start();
            return "web-dashboard     : http://" + bindAddress + ":" + port + "/"
                    + (loginConfig.enabled ? " (Login erforderlich)" : " (KEIN Login - ungeschuetzt!)");
        } catch (Exception e) {
            getLogger().severe("Konnte den Dashboard-Webserver nicht starten: " + e.getMessage());
            return "web-dashboard     : FEHLER (" + e.getMessage() + ")";
        }
    }

    /** "/ecodash reload" - reloads config.yml and restarts just the web server with the new
     *  bind-address/port/login settings. Module toggles and the refresh interval are read once
     *  at startup by their own scheduled tasks and are NOT affected - those need a full
     *  plugin/server restart, which this command deliberately does not attempt (tearing down
     *  and re-registering Bukkit listeners/scheduled tasks safely is out of scope for a
     *  lightweight config reload). Returns the resulting status line for the command to show. */
    public String reloadWebServer() {
        reloadConfig();
        if (httpServer != null) {
            httpServer.stop();
        }
        return startHttpServer();
    }

    public List<String> statusLines() {
        return statusLines;
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

    private com.thedomibusiness.economydashboard.presence.PlayerActivitySnapshot latestPlayerActivitySnapshot() {
        return playerPresenceService != null
                ? playerPresenceService.snapshotNow()
                : com.thedomibusiness.economydashboard.presence.PlayerActivitySnapshot.empty();
    }

    private com.thedomibusiness.economydashboard.economy.EconomyHistorySnapshot latestEconomyHistorySnapshot() {
        return economyHistoryService != null
                ? economyHistoryService.snapshotNow()
                : com.thedomibusiness.economydashboard.economy.EconomyHistorySnapshot.empty();
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
        if (playerPresenceService != null) {
            playerPresenceService.close();
        }
        if (economyHistoryService != null) {
            economyHistoryService.close();
        }
        if (anomalyApprovalService != null) {
            anomalyApprovalService.close();
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
