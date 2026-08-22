package com.thedomibusiness.economydashboard.anomaly;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class AnomalyApprovalService {

    private final Plugin plugin;
    private final AnomalyApprovalDatabase db;

    public AnomalyApprovalService(Plugin plugin) throws SQLException {
        this.plugin = plugin;
        this.db = new AnomalyApprovalDatabase(new File(plugin.getDataFolder(), "anomaly-approvals.db"));
    }

    public void approve(String key, String severity, String category, String title, String description, String linkUrl) {
        try {
            db.approve(key, severity, category, title, description, linkUrl, System.currentTimeMillis());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte Anomalie nicht als geprueft markieren", e);
        }
    }

    public void unapprove(String key) {
        try {
            db.unapprove(key);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte Anomalie nicht aus dem Archiv entfernen", e);
        }
    }

    public Set<String> approvedKeys() {
        try {
            return db.approvedKeys();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte geprüfte Anomalien nicht laden", e);
            return Collections.emptySet();
        }
    }

    public List<ArchivedAnomaly> listArchive() {
        try {
            return db.listAll();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte Anomalie-Archiv nicht laden", e);
            return Collections.emptyList();
        }
    }

    public void close() {
        db.close();
    }
}
