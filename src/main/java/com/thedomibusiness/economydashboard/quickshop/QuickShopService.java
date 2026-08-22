package com.thedomibusiness.economydashboard.quickshop;

import com.thedomibusiness.economydashboard.filter.TransactionFilter;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class QuickShopService implements AutoCloseable {

    private static final int SHOP_LIST_LIMIT = 500;
    private static final int TOP_OWNERS_LIMIT = 500;

    private final Plugin plugin;
    private final QuickShopDatabase db;
    private final QuickShopRegistryCollector registryCollector;

    public QuickShopService(Plugin plugin) throws SQLException {
        this.plugin = plugin;
        this.db = new QuickShopDatabase(new File(plugin.getDataFolder(), "quickshop.db"));
        this.registryCollector = new QuickShopRegistryCollector();
        plugin.getServer().getPluginManager().registerEvents(new QuickShopListener(plugin, db), plugin);
    }

    public void refreshRegistry() {
        try {
            db.replaceShops(registryCollector.collect());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Konnte QuickShop-Registry nicht aktualisieren", e);
        }
    }

    public QuickShopSnapshot snapshotNow() {
        try {
            return db.snapshot(SHOP_LIST_LIMIT, TOP_OWNERS_LIMIT);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte QuickShop-Snapshot nicht aus der Datenbank lesen", e);
            return QuickShopSnapshot.empty();
        }
    }

    public List<QuickShopSnapshot.ShopListing> searchShops(String query, int limit) {
        try {
            return db.searchShops(query, limit);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "QuickShop-Suche fehlgeschlagen", e);
            return Collections.emptyList();
        }
    }

    public String exportShopsCsv(String owner, String item) {
        try {
            return db.exportShopsCsv(owner, item);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "CSV-Export der QuickShop-Registry fehlgeschlagen", e);
            return "";
        }
    }

    public String exportTransactionsCsv(TransactionFilter filter) {
        try {
            return db.exportTransactionsCsv(filter);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "CSV-Export der QuickShop-Transaktionen fehlgeschlagen", e);
            return "";
        }
    }

    public String queryTransactionsJson(TransactionFilter filter) {
        try {
            return db.queryTransactionsJson(filter);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Gefilterte QuickShop-Transaktionen konnten nicht geladen werden", e);
            return "[]";
        }
    }

    public String playerSummaryJson(String playerName) {
        try {
            return db.playerSummaryJson(playerName);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "QuickShop-Spielerprofil konnte nicht geladen werden", e);
            return "null";
        }
    }

    public List<com.thedomibusiness.economydashboard.activity.ActivityEvent> recentActivity(int limit) {
        try {
            return db.recentActivity(limit);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "QuickShop-Aktivitaet konnte nicht geladen werden", e);
            return Collections.emptyList();
        }
    }

    public java.util.List<com.thedomibusiness.economydashboard.anomaly.SellVolumeRow> sellVolumeByItemAndPlayer() {
        try {
            return db.sellVolumeByItemAndPlayer();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Verkaufsvolumen fuer Anomalie-Erkennung konnte nicht geladen werden", e);
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public void close() {
        db.close();
    }
}
