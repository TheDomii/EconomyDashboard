package com.thedomibusiness.economydashboard.presence;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;
import java.util.logging.Level;

public class PlayerPresenceService {

    private static final int DAILY_HISTORY_DAYS = 30;

    private final Plugin plugin;
    private final PlayerPresenceDatabase db;

    public PlayerPresenceService(Plugin plugin) throws SQLException {
        this.plugin = plugin;
        this.db = new PlayerPresenceDatabase(new File(plugin.getDataFolder(), "presence.db"));
        plugin.getServer().getPluginManager().registerEvents(new PlayerPresenceListener(this), plugin);
    }

    public void recordJoin(String player) {
        try {
            db.recordJoin(player, System.currentTimeMillis());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte Spieler-Join nicht speichern", e);
        }
    }

    public void recordQuit(String player) {
        try {
            db.recordQuit(player, System.currentTimeMillis());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte Spieler-Quit nicht speichern", e);
        }
    }

    /** Must run on the main thread - Bukkit.getOnlinePlayers() is a main-thread-only API. */
    public void sampleOnlineCount() {
        int count = plugin.getServer().getOnlinePlayers().size();
        try {
            db.recordSample(System.currentTimeMillis(), count);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte Online-Spieleranzahl nicht speichern", e);
        }
    }

    public PlayerActivitySnapshot snapshotNow() {
        try {
            long[] peak = db.peakSample();
            return new PlayerActivitySnapshot(System.currentTimeMillis(), plugin.getServer().getOnlinePlayers().size(),
                    (int) peak[1], peak[0], db.dailyUniqueCounts(DAILY_HISTORY_DAYS), db.hourlyPattern());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte Spieler-Aktivitaet nicht aus der Datenbank lesen", e);
            return PlayerActivitySnapshot.empty();
        }
    }

    public void close() {
        db.close();
    }
}
