package com.thedomibusiness.economydashboard.economy;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;
import java.util.logging.Level;

public class EconomyHistoryService {

    private static final int DAILY_HISTORY_DAYS = 30;

    private final Plugin plugin;
    private final EconomyHistoryDatabase db;

    public EconomyHistoryService(Plugin plugin) throws SQLException {
        this.plugin = plugin;
        this.db = new EconomyHistoryDatabase(new File(plugin.getDataFolder(), "economy-history.db"));
    }

    public void recordSample(double totalMoney, int playerCount) {
        try {
            db.recordSample(System.currentTimeMillis(), totalMoney, playerCount);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte Geldmengen-Verlauf nicht speichern", e);
        }
    }

    public EconomyHistorySnapshot snapshotNow() {
        try {
            return new EconomyHistorySnapshot(System.currentTimeMillis(), db.dailyAverages(DAILY_HISTORY_DAYS));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte Geldmengen-Verlauf nicht lesen", e);
            return EconomyHistorySnapshot.empty();
        }
    }

    public void close() {
        db.close();
    }
}
