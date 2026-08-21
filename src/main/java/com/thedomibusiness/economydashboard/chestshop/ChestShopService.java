package com.thedomibusiness.economydashboard.chestshop;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class ChestShopService implements AutoCloseable {

    private static final int SHOP_LIST_LIMIT = 500;
    private static final int TOP_OWNERS_LIMIT = 10;

    private final Plugin plugin;
    private final ChestShopDatabase db;
    private final ChestShopListener listener;

    public ChestShopService(Plugin plugin, boolean debug) throws SQLException {
        this.plugin = plugin;
        this.db = new ChestShopDatabase(new File(plugin.getDataFolder(), "chestshop.db"));
        this.listener = new ChestShopListener(plugin, db, debug);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    public ChestShopSnapshot snapshotNow() {
        try {
            return db.snapshot(SHOP_LIST_LIMIT, TOP_OWNERS_LIMIT);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte ChestShop-Snapshot nicht aus der Datenbank lesen", e);
            return ChestShopSnapshot.empty();
        }
    }

    public List<ChestShopSnapshot.ShopListing> searchShops(String query, int limit) {
        try {
            return db.searchShops(query, limit);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "ChestShop-Suche fehlgeschlagen", e);
            return Collections.emptyList();
        }
    }

    @Override
    public void close() {
        db.close();
    }
}
