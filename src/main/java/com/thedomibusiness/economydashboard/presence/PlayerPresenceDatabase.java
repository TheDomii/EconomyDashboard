package com.thedomibusiness.economydashboard.presence;

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
 * Tracks server population over time so the "Spieler" page can show daily unique visitors and
 * when the server is busiest - data that doesn't exist anywhere else, so collection only starts
 * once this table exists (no historical backfill is possible). Two tables: one join/quit session
 * per player visit (for daily unique counts), and one online-count sample per refresh tick (for
 * the hour-of-day pattern and the all-time peak). Persists in getDataFolder()/presence.db.
 */
public class PlayerPresenceDatabase implements AutoCloseable {

    private final Connection connection;

    public PlayerPresenceDatabase(File dbFile) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS sessions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "player TEXT NOT NULL, " +
                    "join_millis INTEGER NOT NULL, " +
                    "quit_millis INTEGER)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_sessions_player ON sessions(player)");
            st.execute("CREATE TABLE IF NOT EXISTS online_samples (" +
                    "timestamp_millis INTEGER PRIMARY KEY, " +
                    "online_count INTEGER NOT NULL)");
        }
    }

    public void recordJoin(String player, long millis) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sessions(player, join_millis, quit_millis) VALUES (?, ?, NULL)")) {
            ps.setString(1, player);
            ps.setLong(2, millis);
            ps.executeUpdate();
        }
    }

    public void recordQuit(String player, long millis) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE sessions SET quit_millis = ? WHERE id = (" +
                        "SELECT id FROM sessions WHERE player = ? AND quit_millis IS NULL " +
                        "ORDER BY join_millis DESC LIMIT 1)")) {
            ps.setLong(1, millis);
            ps.setString(2, player);
            ps.executeUpdate();
        }
    }

    public void recordSample(long millis, int onlineCount) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO online_samples(timestamp_millis, online_count) VALUES (?, ?)")) {
            ps.setLong(1, millis);
            ps.setInt(2, onlineCount);
            ps.executeUpdate();
        }
    }

    /** Unique players per day, newest first. */
    public List<PlayerActivitySnapshot.DailyCount> dailyUniqueCounts(int days) throws SQLException {
        List<PlayerActivitySnapshot.DailyCount> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT strftime('%Y-%m-%d', join_millis / 1000, 'unixepoch') AS day, COUNT(DISTINCT player) " +
                        "FROM sessions GROUP BY day ORDER BY day DESC LIMIT ?")) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new PlayerActivitySnapshot.DailyCount(rs.getString(1), rs.getInt(2)));
                }
            }
        }
        return result;
    }

    /** Average online count for each hour of day (0-23), across all recorded samples. */
    public List<PlayerActivitySnapshot.HourlyAverage> hourlyPattern() throws SQLException {
        List<PlayerActivitySnapshot.HourlyAverage> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT CAST(strftime('%H', timestamp_millis / 1000, 'unixepoch') AS INTEGER) AS hourOfDay, " +
                             "AVG(online_count) FROM online_samples GROUP BY hourOfDay ORDER BY hourOfDay")) {
            while (rs.next()) {
                result.add(new PlayerActivitySnapshot.HourlyAverage(rs.getInt(1), rs.getDouble(2)));
            }
        }
        return result;
    }

    /** timestamp_millis, online_count of the single highest sample ever recorded. */
    public long[] peakSample() throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT timestamp_millis, online_count FROM online_samples " +
                             "ORDER BY online_count DESC, timestamp_millis DESC LIMIT 1")) {
            if (rs.next()) {
                return new long[]{rs.getLong(1), rs.getLong(2)};
            }
        }
        return new long[]{0, 0};
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
