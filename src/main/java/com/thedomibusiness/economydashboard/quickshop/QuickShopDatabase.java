package com.thedomibusiness.economydashboard.quickshop;

import com.thedomibusiness.economydashboard.filter.TransactionFilter;
import com.thedomibusiness.economydashboard.web.CsvBuilder;

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
 * Stores a mirror of QuickShop's own shop registry (refreshed periodically via
 * ShopManager#getAllShops()) plus a transaction history built from
 * ShopSuccessPurchaseEvent. Persists across restarts in getDataFolder()/quickshop.db.
 */
public class QuickShopDatabase implements AutoCloseable {

    private final Connection connection;

    public QuickShopDatabase(File dbFile) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS shops (" +
                    "location TEXT PRIMARY KEY, " +
                    "owner TEXT NOT NULL, " +
                    "item_name TEXT, " +
                    "price REAL NOT NULL DEFAULT 0, " +
                    "shop_buys INTEGER NOT NULL DEFAULT 0, " +
                    "updated_at INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp_millis INTEGER NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "player TEXT NOT NULL, " +
                    "owner TEXT, " +
                    "item_name TEXT, " +
                    "amount INTEGER NOT NULL DEFAULT 0, " +
                    "price REAL NOT NULL DEFAULT 0)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_qs_owner ON shops(owner)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_qs_tx_owner ON transactions(owner)");
        }
    }

    /** Replaces the whole shop registry with the given list (mirrors QuickShop's live state). */
    public void replaceShops(List<QuickShopSnapshot.ShopListing> shops) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM shops");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO shops(location, owner, item_name, price, shop_buys, updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            long now = System.currentTimeMillis();
            for (QuickShopSnapshot.ShopListing s : shops) {
                ps.setString(1, s.location);
                ps.setString(2, s.owner);
                ps.setString(3, s.item);
                ps.setDouble(4, s.price);
                ps.setInt(5, s.shopBuys ? 1 : 0);
                ps.setLong(6, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        connection.commit();
        connection.setAutoCommit(true);
    }

    public void insertTransaction(String type, String player, String owner, String itemName,
                                   int amount, double price) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO transactions(timestamp_millis, type, player, owner, item_name, amount, price) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, type);
            ps.setString(3, player);
            ps.setString(4, owner);
            ps.setString(5, itemName);
            ps.setInt(6, amount);
            ps.setDouble(7, price);
            ps.executeUpdate();
        }
    }

    public QuickShopSnapshot snapshot(int shopListLimit, int topOwnersLimit) throws SQLException {
        List<QuickShopSnapshot.ShopListing> shops = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT owner, item_name, price, shop_buys, location FROM shops " +
                             "ORDER BY owner, item_name LIMIT " + shopListLimit)) {
            while (rs.next()) {
                shops.add(new QuickShopSnapshot.ShopListing(rs.getString(1), rs.getString(2), rs.getDouble(3),
                        rs.getInt(4) == 1, rs.getString(5)));
            }
        }

        int totalShops = 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM shops")) {
            if (rs.next()) totalShops = rs.getInt(1);
        }

        int totalTransactions = 0;
        double totalRevenue = 0;
        double totalPayouts = 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*), " +
                             "COALESCE(SUM(CASE WHEN type = 'BUY' THEN price ELSE 0 END), 0), " +
                             "COALESCE(SUM(CASE WHEN type = 'SELL' THEN price ELSE 0 END), 0) " +
                             "FROM transactions")) {
            if (rs.next()) {
                totalTransactions = rs.getInt(1);
                totalRevenue = rs.getDouble(2);
                totalPayouts = rs.getDouble(3);
            }
        }

        List<QuickShopSnapshot.OwnerStats> topOwners = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT owner, " +
                             "(SELECT COUNT(*) FROM shops s2 WHERE s2.owner = t.owner) AS shop_count, " +
                             "COUNT(*) AS tx_count, " +
                             "COALESCE(SUM(CASE WHEN type = 'BUY' THEN price ELSE 0 END), 0) AS revenue, " +
                             "COALESCE(SUM(CASE WHEN type = 'SELL' THEN price ELSE 0 END), 0) AS payouts " +
                             "FROM transactions t WHERE owner IS NOT NULL " +
                             "GROUP BY owner ORDER BY (revenue - payouts) DESC LIMIT " + topOwnersLimit)) {
            while (rs.next()) {
                topOwners.add(new QuickShopSnapshot.OwnerStats(rs.getString(1), rs.getInt(2), rs.getInt(3),
                        rs.getDouble(4), rs.getDouble(5)));
            }
        }

        return new QuickShopSnapshot(System.currentTimeMillis(), totalShops, totalTransactions,
                totalRevenue, totalPayouts, topOwners, shops);
    }

    public List<QuickShopSnapshot.ShopListing> searchShops(String query, int limit) throws SQLException {
        List<QuickShopSnapshot.ShopListing> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT owner, item_name, price, shop_buys, location FROM shops " +
                        "WHERE owner LIKE ? COLLATE NOCASE OR item_name LIKE ? COLLATE NOCASE " +
                        "ORDER BY owner LIMIT ?")) {
            ps.setString(1, "%" + query + "%");
            ps.setString(2, "%" + query + "%");
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new QuickShopSnapshot.ShopListing(rs.getString(1), rs.getString(2), rs.getDouble(3),
                            rs.getInt(4) == 1, rs.getString(5)));
                }
            }
        }
        return result;
    }

    /** Current shop registry as CSV, with optional owner/item substring filters. */
    public String exportShopsCsv(String owner, String item) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (owner != null && !owner.isEmpty()) {
            where.append(" AND owner LIKE ? COLLATE NOCASE");
            params.add("%" + owner + "%");
        }
        if (item != null && !item.isEmpty()) {
            where.append(" AND item_name LIKE ? COLLATE NOCASE");
            params.add("%" + item + "%");
        }

        CsvBuilder csv = new CsvBuilder();
        csv.header("owner", "item", "price", "type", "location", "updated_at");
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT owner, item_name, price, shop_buys, location, updated_at FROM shops" + where + " ORDER BY owner, item_name")) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    csv.row(rs.getString(1), rs.getString(2), CsvBuilder.formatMoney(rs.getDouble(3)),
                            rs.getInt(4) == 1 ? "BUYING" : "SELLING", rs.getString(5),
                            CsvBuilder.formatTimestamp(rs.getLong(6)));
                }
            }
        }
        return csv.build();
    }

    /** Raw (non-aggregated) transaction rows for CSV export, with optional filters. Newest first. */
    public String exportTransactionsCsv(TransactionFilter filter) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (filter.fromMillis != null) {
            where.append(" AND timestamp_millis >= ?");
            params.add(filter.fromMillis);
        }
        if (filter.toMillis != null) {
            where.append(" AND timestamp_millis <= ?");
            params.add(filter.toMillis);
        }
        if (filter.type != null) {
            where.append(" AND type = ?");
            params.add(filter.type.toUpperCase());
        }
        if (filter.player != null) {
            where.append(" AND player LIKE ? COLLATE NOCASE");
            params.add("%" + filter.player + "%");
        }
        if (filter.counterparty != null) {
            where.append(" AND owner LIKE ? COLLATE NOCASE");
            params.add("%" + filter.counterparty + "%");
        }
        if (filter.item != null) {
            where.append(" AND item_name LIKE ? COLLATE NOCASE");
            params.add("%" + filter.item + "%");
        }
        if (filter.minPrice != null) {
            where.append(" AND price >= ?");
            params.add(filter.minPrice);
        }
        if (filter.maxPrice != null) {
            where.append(" AND price <= ?");
            params.add(filter.maxPrice);
        }

        String sql = "SELECT timestamp_millis, type, player, owner, item_name, amount, price FROM transactions"
                + where + " ORDER BY timestamp_millis DESC LIMIT ?";
        params.add(filter.limit);

        CsvBuilder csv = new CsvBuilder();
        csv.header("timestamp", "type", "player", "owner", "item", "amount", "price");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    csv.row(CsvBuilder.formatTimestamp(rs.getLong(1)), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getInt(6), CsvBuilder.formatMoney(rs.getDouble(7)));
                }
            }
        }
        return csv.build();
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
