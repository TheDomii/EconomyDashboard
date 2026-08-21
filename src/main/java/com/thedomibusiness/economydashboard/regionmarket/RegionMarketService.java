package com.thedomibusiness.economydashboard.regionmarket;

import com.thedomibusiness.economydashboard.filter.TransactionFilter;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class RegionMarketService {

    private static final int REGION_LIST_LIMIT = 500;
    private static final int TOP_OWNERS_LIMIT = 500;

    private final Plugin plugin;
    private final RegionMarketDatabase db;
    private final RegionMarketCollector collector;

    public RegionMarketService(Plugin plugin) throws SQLException {
        this.plugin = plugin;
        this.db = new RegionMarketDatabase(new File(plugin.getDataFolder(), "regionmarket.db"));
        this.collector = new RegionMarketCollector();
    }

    public void refreshRegistry() {
        try {
            db.replaceRegions(collector.collect());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Konnte AdvancedRegionMarket-Registry nicht aktualisieren", e);
        }
    }

    public RegionMarketSnapshot snapshotNow() {
        try {
            return db.snapshot(REGION_LIST_LIMIT, TOP_OWNERS_LIMIT);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte AdvancedRegionMarket-Snapshot nicht aus der Datenbank lesen", e);
            return RegionMarketSnapshot.empty();
        }
    }

    public List<RegionMarketSnapshot.RegionListing> searchRegions(String query, int limit) {
        try {
            return db.searchRegions(query, limit);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "AdvancedRegionMarket-Suche fehlgeschlagen", e);
            return Collections.emptyList();
        }
    }

    public String exportRegionsCsv(String owner, String world, Boolean sold) {
        try {
            return db.exportRegionsCsv(owner, world, sold);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "CSV-Export der AdvancedRegionMarket-Registry fehlgeschlagen", e);
            return "";
        }
    }

    public String exportTransactionsCsv(TransactionFilter filter) {
        try {
            return db.exportTransactionsCsv(filter);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "CSV-Export der AdvancedRegionMarket-Transaktionen fehlgeschlagen", e);
            return "";
        }
    }

    public void close() {
        db.close();
    }
}
