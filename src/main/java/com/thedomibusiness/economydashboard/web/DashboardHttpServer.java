package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpServer;
import com.thedomibusiness.economydashboard.auth.AuthFilter;
import com.thedomibusiness.economydashboard.auth.LoginConfig;
import com.thedomibusiness.economydashboard.auth.SessionManager;
import com.thedomibusiness.economydashboard.chestshop.ChestShopSnapshot;
import com.thedomibusiness.economydashboard.economy.EconomySnapshot;
import com.thedomibusiness.economydashboard.quickshop.QuickShopSnapshot;
import com.thedomibusiness.economydashboard.search.SearchService;
import com.thedomibusiness.economydashboard.towny.TownySnapshot;
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
    private final Supplier<ChestShopSnapshot> chestShopSnapshotSupplier;
    private final Supplier<QuickShopSnapshot> quickShopSnapshotSupplier;
    private final SearchService searchService;
    private final LoginConfig loginConfig;
    private HttpServer server;

    public DashboardHttpServer(Plugin plugin, String bindAddress, int port,
                                Supplier<EconomySnapshot> snapshotSupplier,
                                Supplier<TraderSnapshot> traderSnapshotSupplier,
                                Supplier<List<ShopPriceEntry>> pricesSupplier,
                                Supplier<TownySnapshot> townySnapshotSupplier,
                                Supplier<ChestShopSnapshot> chestShopSnapshotSupplier,
                                Supplier<QuickShopSnapshot> quickShopSnapshotSupplier,
                                SearchService searchService,
                                LoginConfig loginConfig) {
        this.plugin = plugin;
        this.bindAddress = bindAddress;
        this.port = port;
        this.snapshotSupplier = snapshotSupplier;
        this.traderSnapshotSupplier = traderSnapshotSupplier;
        this.pricesSupplier = pricesSupplier;
        this.townySnapshotSupplier = townySnapshotSupplier;
        this.chestShopSnapshotSupplier = chestShopSnapshotSupplier;
        this.quickShopSnapshotSupplier = quickShopSnapshotSupplier;
        this.searchService = searchService;
        this.loginConfig = loginConfig;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);

        HttpContext economyCtx = server.createContext("/api/economy/overview", new EconomyApiHandler(snapshotSupplier));
        HttpContext tradersCtx = server.createContext("/api/traders/overview", new TraderApiHandler(traderSnapshotSupplier));
        HttpContext pricesCtx = server.createContext("/api/traders/prices", new ShopPricesApiHandler(pricesSupplier));
        HttpContext townyCtx = server.createContext("/api/towny/overview", new TownyApiHandler(townySnapshotSupplier));
        HttpContext chestShopCtx = server.createContext("/api/chestshops/overview", new ChestShopApiHandler(chestShopSnapshotSupplier));
        HttpContext quickShopCtx = server.createContext("/api/quickshops/overview", new QuickShopApiHandler(quickShopSnapshotSupplier));
        HttpContext searchCtx = server.createContext("/api/search", new SearchApiHandler(searchService));
        HttpContext staticCtx = server.createContext("/", new StaticFileHandler(plugin));

        if (loginConfig.enabled) {
            SessionManager sessionManager = new SessionManager(loginConfig.sessionMinutes);
            Filter authFilter = new AuthFilter(sessionManager);

            for (HttpContext ctx : new HttpContext[]{economyCtx, tradersCtx, pricesCtx, townyCtx, chestShopCtx, quickShopCtx, searchCtx, staticCtx}) {
                ctx.getFilters().add(authFilter);
            }

            LoginPageHandler loginPageHandler = new LoginPageHandler(plugin, loginConfig);
            LoginPostHandler loginPostHandler = new LoginPostHandler(plugin, loginConfig, sessionManager);
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
