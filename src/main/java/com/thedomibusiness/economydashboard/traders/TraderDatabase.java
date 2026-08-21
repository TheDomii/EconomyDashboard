package com.thedomibusiness.economydashboard.traders;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite-backed storage for parsed dtlTradersPlus transactions, so totals and the
 * per-file read progress survive plugin/server restarts. One file: getDataFolder()/data.db.
 */
public class TraderDatabase implements AutoCloseable {

    private final Connection connection;

    public TraderDatabase(File dbFile) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS processed_files (" +
                    "file_path TEXT PRIMARY KEY, " +
                    "line_count INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp_millis INTEGER NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "player TEXT NOT NULL, " +
                    "shop TEXT NOT NULL, " +
                    "page TEXT, " +
                    "item_name TEXT, " +
                    "item_amount INTEGER NOT NULL DEFAULT 0, " +
                    "price REAL NOT NULL DEFAULT 0)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tx_shop ON transactions(shop)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_tx_item ON transactions(item_name)");
        }
    }

    public void beginBatch() throws SQLException {
        connection.setAutoCommit(false);
    }

    public void commitBatch() throws SQLException {
        connection.commit();
        connection.setAutoCommit(true);
    }

    public void rollbackBatch() {
        try {
            connection.rollback();
            connection.setAutoCommit(true);
        } catch (SQLException ignored) {
        }
    }

    public int getProcessedLineCount(String filePath) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT line_count FROM processed_files WHERE file_path = ?")) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void setProcessedLineCount(String filePath, int count) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO processed_files(file_path, line_count) VALUES (?, ?) " +
                        "ON CONFLICT(file_path) DO UPDATE SET line_count = excluded.line_count")) {
            ps.setString(1, filePath);
            ps.setInt(2, count);
            ps.executeUpdate();
        }
    }

    public void insertTransaction(Transaction tx) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO transactions(timestamp_millis, type, player, shop, page, item_name, item_amount, price) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, tx.type.name());
            ps.setString(3, tx.player);
            ps.setString(4, tx.shop);
            ps.setString(5, tx.page);
            if (tx.itemName != null) {
                ps.setString(6, tx.itemName);
            } else {
                ps.setNull(6, Types.VARCHAR);
            }
            ps.setInt(7, tx.itemAmount);
            ps.setDouble(8, tx.price);
            ps.executeUpdate();
        }
    }

    public TraderSnapshot snapshot() throws SQLException {
        int totalTransactions = 0;
        double totalRevenue = 0;
        double totalPayouts = 0;
        double totalTradeVolume = 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*), " +
                             "COALESCE(SUM(CASE WHEN type = 'BUY' THEN price ELSE 0 END), 0), " +
                             "COALESCE(SUM(CASE WHEN type = 'SELL' THEN price ELSE 0 END), 0), " +
                             "COALESCE(SUM(CASE WHEN type = 'TRADE' THEN price ELSE 0 END), 0) " +
                             "FROM transactions")) {
            if (rs.next()) {
                totalTransactions = rs.getInt(1);
                totalRevenue = rs.getDouble(2);
                totalPayouts = rs.getDouble(3);
                totalTradeVolume = rs.getDouble(4);
            }
        }

        List<TraderSnapshot.ShopStats> shops = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT shop, COUNT(*), " +
                             "COALESCE(SUM(CASE WHEN type = 'BUY' THEN price ELSE 0 END), 0) AS revenue, " +
                             "COALESCE(SUM(CASE WHEN type = 'SELL' THEN price ELSE 0 END), 0) AS payouts " +
                             "FROM transactions GROUP BY shop ORDER BY (revenue - payouts) DESC LIMIT 10")) {
            while (rs.next()) {
                shops.add(new TraderSnapshot.ShopStats(rs.getString(1), rs.getInt(2), rs.getDouble(3), rs.getDouble(4)));
            }
        }

        List<TraderSnapshot.ItemStats> items = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT item_name, " +
                             "COALESCE(SUM(CASE WHEN type = 'BUY' THEN item_amount ELSE 0 END), 0) AS bought, " +
                             "COALESCE(SUM(CASE WHEN type = 'SELL' THEN item_amount ELSE 0 END), 0) AS sold " +
                             "FROM transactions WHERE item_name IS NOT NULL " +
                             "GROUP BY item_name ORDER BY (bought + sold) DESC LIMIT 10")) {
            while (rs.next()) {
                items.add(new TraderSnapshot.ItemStats(rs.getString(1), rs.getInt(2), rs.getInt(3)));
            }
        }

        return new TraderSnapshot(System.currentTimeMillis(), totalTransactions, totalRevenue, totalPayouts,
                totalTradeVolume, shops, items);
    }

    public List<TraderSnapshot.ShopStats> searchShops(String query, int limit) throws SQLException {
        List<TraderSnapshot.ShopStats> shops = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT shop, COUNT(*), " +
                        "COALESCE(SUM(CASE WHEN type = 'BUY' THEN price ELSE 0 END), 0) AS revenue, " +
                        "COALESCE(SUM(CASE WHEN type = 'SELL' THEN price ELSE 0 END), 0) AS payouts " +
                        "FROM transactions WHERE shop LIKE ? COLLATE NOCASE " +
                        "GROUP BY shop ORDER BY (revenue - payouts) DESC LIMIT ?")) {
            ps.setString(1, "%" + query + "%");
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    shops.add(new TraderSnapshot.ShopStats(rs.getString(1), rs.getInt(2), rs.getDouble(3), rs.getDouble(4)));
                }
            }
        }
        return shops;
    }

    public List<TraderSnapshot.ItemStats> searchItems(String query, int limit) throws SQLException {
        List<TraderSnapshot.ItemStats> items = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT item_name, " +
                        "COALESCE(SUM(CASE WHEN type = 'BUY' THEN item_amount ELSE 0 END), 0) AS bought, " +
                        "COALESCE(SUM(CASE WHEN type = 'SELL' THEN item_amount ELSE 0 END), 0) AS sold " +
                        "FROM transactions WHERE item_name LIKE ? COLLATE NOCASE " +
                        "GROUP BY item_name ORDER BY (bought + sold) DESC LIMIT ?")) {
            ps.setString(1, "%" + query + "%");
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new TraderSnapshot.ItemStats(rs.getString(1), rs.getInt(2), rs.getInt(3)));
                }
            }
        }
        return items;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
