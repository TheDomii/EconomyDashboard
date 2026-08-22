package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import com.thedomibusiness.economydashboard.activity.ActivityFeedService;
import com.thedomibusiness.economydashboard.anomaly.AnomalyApprovalService;
import com.thedomibusiness.economydashboard.anomaly.AnomalyReport;
import com.thedomibusiness.economydashboard.auth.AuthFilter;
import com.thedomibusiness.economydashboard.auth.LoginConfig;
import com.thedomibusiness.economydashboard.auth.SessionManager;
import com.thedomibusiness.economydashboard.economy.EconomyHistorySnapshot;
import com.thedomibusiness.economydashboard.economy.EconomySnapshot;
import com.thedomibusiness.economydashboard.presence.PlayerActivitySnapshot;
import com.thedomibusiness.economydashboard.quickshop.QuickShopService;
import com.thedomibusiness.economydashboard.quickshop.QuickShopSnapshot;
import com.thedomibusiness.economydashboard.regionmarket.RegionMarketService;
import com.thedomibusiness.economydashboard.regionmarket.RegionMarketSnapshot;
import com.thedomibusiness.economydashboard.search.SearchService;
import com.thedomibusiness.economydashboard.towny.TownyCollector;
import com.thedomibusiness.economydashboard.towny.TownySnapshot;
import com.thedomibusiness.economydashboard.traders.DtlTradersLogCollector;
import com.thedomibusiness.economydashboard.traders.ShopPriceEntry;
import com.thedomibusiness.economydashboard.traders.TraderSnapshot;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class DashboardHttpServer {

    private final Plugin plugin;
    private final String bindAddress;
    private final int port;
    private final Supplier<EconomySnapshot> snapshotSupplier;
    private final Supplier<TraderSnapshot> traderSnapshotSupplier;
    private final Supplier<List<ShopPriceEntry>> pricesSupplier;
    private final Supplier<TownySnapshot> townySnapshotSupplier;
    private final Supplier<QuickShopSnapshot> quickShopSnapshotSupplier;
    private final Supplier<RegionMarketSnapshot> regionMarketSnapshotSupplier;
    private final Supplier<AnomalyReport> anomalyReportSupplier;
    private final Supplier<PlayerActivitySnapshot> playerActivitySupplier;
    private final Supplier<EconomyHistorySnapshot> economyHistorySupplier;
    private final DtlTradersLogCollector tradersCollector;
    private final QuickShopService quickShopService;
    private final RegionMarketService regionMarketService;
    private final TownyCollector townyCollector;
    private final SearchService searchService;
    private final ActivityFeedService activityFeedService;
    private final AnomalyApprovalService anomalyApprovalService;
    private final LoginConfig loginConfig;
    private HttpServer server;

    public DashboardHttpServer(Plugin plugin, String bindAddress, int port,
                                Supplier<EconomySnapshot> snapshotSupplier,
                                Supplier<TraderSnapshot> traderSnapshotSupplier,
                                Supplier<List<ShopPriceEntry>> pricesSupplier,
                                Supplier<TownySnapshot> townySnapshotSupplier,
                                Supplier<QuickShopSnapshot> quickShopSnapshotSupplier,
                                Supplier<RegionMarketSnapshot> regionMarketSnapshotSupplier,
                                Supplier<AnomalyReport> anomalyReportSupplier,
                                Supplier<PlayerActivitySnapshot> playerActivitySupplier,
                                Supplier<EconomyHistorySnapshot> economyHistorySupplier,
                                DtlTradersLogCollector tradersCollector,
                                QuickShopService quickShopService,
                                RegionMarketService regionMarketService,
                                TownyCollector townyCollector,
                                SearchService searchService,
                                ActivityFeedService activityFeedService,
                                AnomalyApprovalService anomalyApprovalService,
                                LoginConfig loginConfig) {
        this.plugin = plugin;
        this.bindAddress = bindAddress;
        this.port = port;
        this.snapshotSupplier = snapshotSupplier;
        this.traderSnapshotSupplier = traderSnapshotSupplier;
        this.pricesSupplier = pricesSupplier;
        this.townySnapshotSupplier = townySnapshotSupplier;
        this.quickShopSnapshotSupplier = quickShopSnapshotSupplier;
        this.regionMarketSnapshotSupplier = regionMarketSnapshotSupplier;
        this.anomalyReportSupplier = anomalyReportSupplier;
        this.playerActivitySupplier = playerActivitySupplier;
        this.economyHistorySupplier = economyHistorySupplier;
        this.tradersCollector = tradersCollector;
        this.quickShopService = quickShopService;
        this.regionMarketService = regionMarketService;
        this.townyCollector = townyCollector;
        this.searchService = searchService;
        this.activityFeedService = activityFeedService;
        this.anomalyApprovalService = anomalyApprovalService;
        this.loginConfig = loginConfig;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);

        HttpContext economyCtx = server.createContext("/api/economy/overview", new EconomyApiHandler(snapshotSupplier));
        HttpContext tradersCtx = server.createContext("/api/traders/overview", new TraderApiHandler(traderSnapshotSupplier));
        HttpContext pricesCtx = server.createContext("/api/traders/prices", new ShopPricesApiHandler(pricesSupplier));
        HttpContext townyCtx = server.createContext("/api/towny/overview", new TownyApiHandler(townySnapshotSupplier));
        HttpContext quickShopCtx = server.createContext("/api/quickshops/overview", new QuickShopApiHandler(quickShopSnapshotSupplier));
        HttpContext regionMarketCtx = server.createContext("/api/regionmarket/overview", new RegionMarketApiHandler(regionMarketSnapshotSupplier));
        HttpContext searchCtx = server.createContext("/api/search", new SearchApiHandler(searchService));
        HttpContext staticCtx = server.createContext("/", new StaticFileHandler(plugin));

        HttpContext economyCsvCtx = server.createContext("/api/economy/export.csv", new EconomyCsvExportHandler(snapshotSupplier));
        HttpContext pricesCsvCtx = server.createContext("/api/traders/prices/export.csv", new TraderPricesCsvExportHandler(pricesSupplier));
        HttpContext tradersCsvCtx = server.createContext("/api/traders/transactions/export.csv", new TraderTransactionsCsvExportHandler(tradersCollector));
        HttpContext townyCsvCtx = server.createContext("/api/towny/export.csv", new TownyCsvExportHandler(townySnapshotSupplier));
        HttpContext townyNationsCsvCtx = server.createContext("/api/towny/nations/export.csv", new TownyNationsCsvExportHandler(townySnapshotSupplier));
        HttpContext quickShopRegistryCsvCtx = server.createContext("/api/quickshops/export.csv", new QuickShopCsvExportHandlers.Registry(quickShopService));
        HttpContext quickShopTxCsvCtx = server.createContext("/api/quickshops/transactions/export.csv", new QuickShopCsvExportHandlers.Transactions(quickShopService));
        HttpContext regionMarketRegistryCsvCtx = server.createContext("/api/regionmarket/export.csv", new RegionMarketCsvExportHandlers.Registry(regionMarketService));
        HttpContext regionMarketTxCsvCtx = server.createContext("/api/regionmarket/transactions/export.csv", new RegionMarketCsvExportHandlers.Transactions(regionMarketService));

        // JSON counterparts of the transaction CSV exports, for the live filtered table
        // preview on each dashboard page (small capped limit, not meant for bulk download).
        HttpContext tradersTxJsonCtx = server.createContext("/api/traders/transactions", new TraderTransactionsApiHandler(tradersCollector));
        HttpContext quickShopTxJsonCtx = server.createContext("/api/quickshops/transactions", new QuickShopTransactionsApiHandler(quickShopService));
        HttpContext regionMarketTxJsonCtx = server.createContext("/api/regionmarket/transactions", new RegionMarketTransactionsApiHandler(regionMarketService));

        // Cross-module player profile - combines economy, traders, quickshop, regionmarket
        // and (main-thread, via Bukkit's scheduler) towny data for one player name.
        HttpContext playerCtx = server.createContext("/api/player", new PlayerProfileApiHandler(plugin,
                snapshotSupplier, tradersCollector, quickShopService, regionMarketService, townyCollector));

        // Anomaly detection results (see AnomalyDetector) - the "Handlungsbedarf" panel on the overview page.
        HttpContext anomaliesCtx = server.createContext("/api/anomalies", new AnomalyApiHandler(anomalyReportSupplier));

        // Merged recent-transactions feed across modules - the "Live-Aktivität" panel on the overview page.
        HttpContext activityCtx = server.createContext("/api/activity", new ActivityFeedApiHandler(activityFeedService));

        // Server population over time - the "Spieler" page (daily unique players, peak online, hour-of-day pattern).
        HttpContext playerActivityCtx = server.createContext("/api/players/activity", new PlayerActivityApiHandler(playerActivitySupplier));

        // Money-supply trend over time - the "Geldmenge über Zeit" panel on the Wirtschaft page.
        HttpContext economyHistoryCtx = server.createContext("/api/economy/history", new EconomyHistoryApiHandler(economyHistorySupplier));

        // Reviewing anomalies ("geprüft, alles ok") - removes them from "Handlungsbedarf" and
        // keeps them visible on the Archiv page.
        HttpContext anomalyApproveCtx = server.createContext("/api/anomalies/approve", new AnomalyApproveHandler(anomalyApprovalService));
        HttpContext anomalyUnapproveCtx = server.createContext("/api/anomalies/unapprove", new AnomalyUnapproveHandler(anomalyApprovalService));
        HttpContext anomalyArchiveCtx = server.createContext("/api/anomalies/archive", new AnomalyArchiveApiHandler(anomalyApprovalService));

        // CSV export for the three data views that only got JSON endpoints when they were built.
        HttpContext playerActivityCsvCtx = server.createContext("/api/players/activity/export.csv", new PlayerActivityCsvExportHandler(playerActivitySupplier));
        HttpContext economyHistoryCsvCtx = server.createContext("/api/economy/history/export.csv", new EconomyHistoryCsvExportHandler(economyHistorySupplier));
        HttpContext anomalyArchiveCsvCtx = server.createContext("/api/anomalies/archive/export.csv", new AnomalyArchiveCsvExportHandler(anomalyApprovalService));

        if (loginConfig.enabled) {
            SessionManager sessionManager = new SessionManager(loginConfig.sessionMinutes);
            Filter authFilter = new AuthFilter(sessionManager);

            for (HttpContext ctx : new HttpContext[]{economyCtx, tradersCtx, pricesCtx, townyCtx, quickShopCtx, regionMarketCtx, searchCtx, staticCtx,
                    economyCsvCtx, pricesCsvCtx, tradersCsvCtx, townyCsvCtx, townyNationsCsvCtx, quickShopRegistryCsvCtx, quickShopTxCsvCtx,
                    regionMarketRegistryCsvCtx, regionMarketTxCsvCtx,
                    tradersTxJsonCtx, quickShopTxJsonCtx, regionMarketTxJsonCtx, playerCtx, anomaliesCtx,
                    activityCtx, playerActivityCtx, economyHistoryCtx,
                    anomalyApproveCtx, anomalyUnapproveCtx, anomalyArchiveCtx,
                    playerActivityCsvCtx, economyHistoryCsvCtx, anomalyArchiveCsvCtx}) {
                ctx.getFilters().add(authFilter);
            }

            com.thedomibusiness.economydashboard.auth.LoginRateLimiter rateLimiter = new com.thedomibusiness.economydashboard.auth.LoginRateLimiter();
            LoginPageHandler loginPageHandler = new LoginPageHandler(plugin, loginConfig, rateLimiter);
            LoginPostHandler loginPostHandler = new LoginPostHandler(plugin, loginConfig, sessionManager, rateLimiter);
            server.createContext("/login", exchange -> {
                if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    loginPageHandler.handle(exchange);
                } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    loginPostHandler.handle(exchange);
                } else {
                    exchange.sendResponseHeaders(405, -1);
                    exchange.close();
                }
            });
            server.createContext("/logout", new LogoutHandler(sessionManager));
            if (loginConfig.hasBackground() && !loginConfig.isBackgroundUrl()) {
                server.createContext("/login-background", new LoginBackgroundHandler(plugin, loginConfig.backgroundImage));
            }

            if ("changeme".equals(loginConfig.password)) {
                plugin.getLogger().warning("Dashboard-Login benutzt noch das Standardpasswort 'changeme' - bitte in config.yml aendern!");
            }
        }

        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
