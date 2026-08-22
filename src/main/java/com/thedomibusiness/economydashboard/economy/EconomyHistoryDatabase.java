package com.thedomibusiness.economydashboard.economy;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Tracks total money in circulation over time, sampled on the same schedule as the economy
 * refresh (see EconomyDashboardPlugin) - no historical backfill possible, collection starts
 * once this table exists. Same pattern as PlayerPresenceDatabase's online-count samples, just
 * for the "Geldmenge über Zeit" trend on the Wirtschaft page. Persists in
 * getDataFolder()/economy-history.db.
 */
public class EconomyHistoryDatabase implements AutoCloseable {

    private final Connection connection;

    public EconomyHistoryDatabase(File dbFile) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS samples (" +
                    "timestamp_millis INTEGER PRIMARY KEY, " +
                    "total_money REAL NOT NULL, " +
                    "player_count INTEGER NOT NULL)");
        }
    }

    public void recordSample(long millis, double totalMoney, int playerCount) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO samples(timestamp_millis, total_money, player_count) VALUES (?, ?, ?)")) {
            ps.setLong(1, millis);
            ps.setDouble(2, totalMoney);
            ps.setInt(3, playerCount);
            ps.executeUpdate();
        }
    }

    /** One point per day (average total money that day), newest first. */
    public List<EconomyHistorySnapshot.DailyMoneyPoint> dailyAverages(int days) throws SQLException {
        List<EconomyHistorySnapshot.DailyMoneyPoint> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT strftime('%Y-%m-%d', timestamp_millis / 1000, 'unixepoch') AS day, " +
                        "AVG(total_money), MAX(player_count) FROM samples GROUP BY day ORDER BY day DESC LIMIT ?")) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new EconomyHistorySnapshot.DailyMoneyPoint(rs.getString(1), rs.getDouble(2), rs.getInt(3)));
                }
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
