package com.thedomibusiness.economydashboard.traders;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

/**
 * Reads dtlTradersPlus' per-shop log files (plugins/dtlTradersPlus/shops/&lt;shop&gt;/logs/&lt;date&gt;.log),
 * parses new lines with {@link DtlLogParser} and stores them in a SQLite database
 * (getDataFolder()/data.db) so totals survive restarts. Each file's already-processed
 * line count is stored alongside, so a restart resumes instead of re-counting from zero.
 */
public class DtlTradersLogCollector implements AutoCloseable {

    private final Plugin plugin;
    private final File shopsRoot;
    private final DtlLogParser parser = new DtlLogParser();
    private final TraderDatabase db;

    public DtlTradersLogCollector(Plugin plugin) throws SQLException {
        this.plugin = plugin;
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        this.shopsRoot = new File(pluginsFolder, "dtlTradersPlus/shops");
        this.db = new TraderDatabase(new File(plugin.getDataFolder(), "data.db"));
    }

    public void update() {
        File[] shopDirs = shopsRoot.listFiles(File::isDirectory);
        if (shopDirs == null) {
            return;
        }

        try {
            db.beginBatch();
            for (File shopDir : shopDirs) {
                File logsDir = new File(shopDir, "logs");
                File[] logFiles = logsDir.listFiles((dir, name) -> name.endsWith(".log"));
                if (logFiles == null) {
                    continue;
                }
                for (File logFile : logFiles) {
                    processFile(logFile);
                }
            }
            db.commitBatch();
        } catch (SQLException e) {
            db.rollbackBatch();
            plugin.getLogger().log(Level.WARNING, "Konnte dtlTradersPlus-Logs nicht in die Datenbank schreiben", e);
        }
    }

    private void processFile(File file) throws SQLException {
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return;
        }

        String key = file.getAbsolutePath();
        int alreadyProcessed = db.getProcessedLineCount(key);
        if (alreadyProcessed > lines.size()) {
            alreadyProcessed = 0;
        }
        for (int i = alreadyProcessed; i < lines.size(); i++) {
            parser.parse(lines.get(i)).ifPresent(tx -> {
                try {
                    db.insertTransaction(tx);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Konnte Transaktion nicht speichern", e);
                }
            });
        }
        db.setProcessedLineCount(key, lines.size());
    }

    public TraderSnapshot snapshotNow() {
        try {
            return db.snapshot();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte Haendler-Snapshot nicht aus der Datenbank lesen", e);
            return TraderSnapshot.empty();
        }
    }

    public List<TraderSnapshot.ShopStats> searchShops(String query, int limit) {
        try {
            return db.searchShops(query, limit);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Shop-Suche fehlgeschlagen", e);
            return java.util.Collections.emptyList();
        }
    }

    public List<TraderSnapshot.ItemStats> searchItems(String query, int limit) {
        try {
            return db.searchItems(query, limit);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Item-Suche fehlgeschlagen", e);
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public void close() {
        db.close();
    }
}
