package com.thedomibusiness.economydashboard.anomaly;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores anomalies a human has reviewed and approved ("checked, this is fine") so the
 * "Handlungsbedarf" panel stops re-flagging them every detection cycle, and so they stay
 * visible in an archive. Identified by Anomaly#key (category+title), not a numeric id, since
 * anomalies are recomputed from scratch each cycle rather than being persistent entities.
 * Persists in getDataFolder()/anomaly-approvals.db.
 */
public class AnomalyApprovalDatabase implements AutoCloseable {

    private final Connection connection;

    public AnomalyApprovalDatabase(File dbFile) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS approvals (" +
                    "key TEXT PRIMARY KEY, " +
                    "severity TEXT NOT NULL, " +
                    "category TEXT NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "description TEXT NOT NULL, " +
                    "link_url TEXT, " +
                    "approved_at_millis INTEGER NOT NULL)");
        }
    }

    public void approve(String key, String severity, String category, String title, String description,
                         String linkUrl, long millis) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO approvals(key, severity, category, title, description, link_url, approved_at_millis) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, severity);
            ps.setString(3, category);
            ps.setString(4, title);
            ps.setString(5, description);
            if (linkUrl != null && !linkUrl.isEmpty()) {
                ps.setString(6, linkUrl);
            } else {
                ps.setNull(6, Types.VARCHAR);
            }
            ps.setLong(7, millis);
            ps.executeUpdate();
        }
    }

    public void unapprove(String key) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM approvals WHERE key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        }
    }

    public Set<String> approvedKeys() throws SQLException {
        Set<String> result = new HashSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT key FROM approvals")) {
            while (rs.next()) {
                result.add(rs.getString(1));
            }
        }
        return result;
    }

    /** Newest approval first. */
    public List<ArchivedAnomaly> listAll() throws SQLException {
        List<ArchivedAnomaly> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT key, severity, category, title, description, link_url, approved_at_millis " +
                             "FROM approvals ORDER BY approved_at_millis DESC")) {
            while (rs.next()) {
                result.add(new ArchivedAnomaly(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7)));
            }
        }
        return result;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
