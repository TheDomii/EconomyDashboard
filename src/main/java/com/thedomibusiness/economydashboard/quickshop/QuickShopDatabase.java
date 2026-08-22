package com.thedomibusiness.economydashboard.quickshop;

import com.thedomibusiness.economydashboard.filter.TransactionFilter;
import com.thedomibusiness.economydashboard.web.CsvBuilder;
import com.thedomibusiness.economydashboard.web.JsonUtil;

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
        try {
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
        } catch (SQLException e) {
            // A failed statement (e.g. disk full) can leave SQLite's own transaction state
            // already rolled back while the JDBC connection still thinks one is open - calling
            // rollback() here can itself throw "no transaction is active". Either way, the
            // finally block's setAutoCommit(true) is what actually matters: without it, every
            // future call starts an ever-deeper stuck transaction and never recovers, even once
            // the original problem (e.g. the full disk) is gone.
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
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

    /** One row per (item, player) with their total sold quantity - raw material for anomaly detection. */
    public List<com.thedomibusiness.economydashboard.anomaly.SellVolumeRow> sellVolumeByItemAndPlayer() throws SQLException {
        List<com.thedomibusiness.economydashboard.anomaly.SellVolumeRow> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT item_name, player, SUM(amount) FROM transactions " +
                             "WHERE type = 'SELL' AND item_name IS NOT NULL AND player IS NOT NULL " +
                             "GROUP BY item_name, player")) {
            while (rs.next()) {
                result.add(new com.thedomibusiness.economydashboard.anomaly.SellVolumeRow(
                        rs.getString(1), rs.getString(2), rs.getLong(3), "QuickShop"));
            }
        }
        return result;
    }

    /** Most recent transactions as human-readable activity lines, newest first - for the "Live-Aktivität" feed. */
    public List<com.thedomibusiness.economydashboard.activity.ActivityEvent> recentActivity(int limit) throws SQLException {
        List<com.thedomibusiness.economydashboard.activity.ActivityEvent> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT timestamp_millis, type, player, owner, item_name, amount, price FROM transactions " +
                        "ORDER BY timestamp_millis DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long ts = rs.getLong(1);
                    String type = rs.getString(2);
                    String player = rs.getString(3);
                    String owner = rs.getString(4);
                    String item = rs.getString(5);
                    int amount = rs.getInt(6);
                    double price = rs.getDouble(7);
                    String desc = "BUY".equals(type)
                            ? player + " kaufte " + amount + "x " + item + " im Shop von " + owner + " fuer " + JsonUtil.money(price)
                            : player + " verkaufte " + amount + "x " + item + " an den Shop von " + owner + " fuer " + JsonUtil.money(price);
                    result.add(new com.thedomibusiness.economydashboard.activity.ActivityEvent(ts, "QuickShop", player, desc));
                }
            }
        }
        return result;
    }

    private static class FilterSql {
        final StringBuilder where = new StringBuilder(" WHERE 1=1");
        final List<Object> params = new ArrayList<>();
    }

    private FilterSql buildFilterSql(TransactionFilter filter) {
        FilterSql f = new FilterSql();
        if (filter.fromMillis != null) {
            f.where.append(" AND timestamp_millis >= ?");
            f.params.add(filter.fromMillis);
        }
        if (filter.toMillis != null) {
            f.where.append(" AND timestamp_millis <= ?");
            f.params.add(filter.toMillis);
        }
        if (filter.type != null) {
            f.where.append(" AND type = ?");
            f.params.add(filter.type.toUpperCase());
        }
        if (filter.player != null) {
            f.where.append(" AND player LIKE ? COLLATE NOCASE");
            f.params.add("%" + filter.player + "%");
        }
        if (filter.counterparty != null) {
            f.where.append(" AND owner LIKE ? COLLATE NOCASE");
            f.params.add("%" + filter.counterparty + "%");
        }
        if (filter.item != null) {
            f.where.append(" AND item_name LIKE ? COLLATE NOCASE");
            f.params.add("%" + filter.item + "%");
        }
        if (filter.minPrice != null) {
            f.where.append(" AND price >= ?");
            f.params.add(filter.minPrice);
        }
        if (filter.maxPrice != null) {
            f.where.append(" AND price <= ?");
            f.params.add(filter.maxPrice);
        }
        return f;
    }

    /** Raw (non-aggregated) transaction rows for CSV export, with optional filters. Newest first. */
    public String exportTransactionsCsv(TransactionFilter filter) throws SQLException {
        FilterSql f = buildFilterSql(filter);

        String sql = "SELECT timestamp_millis, type, player, owner, item_name, amount, price FROM transactions"
                + f.where + " ORDER BY timestamp_millis DESC LIMIT ?";
        f.params.add(filter.limit);

        CsvBuilder csv = new CsvBuilder();
        csv.header("timestamp", "type", "player", "owner", "item", "amount", "price");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < f.params.size(); i++) {
                ps.setObject(i + 1, f.params.get(i));
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

    /** Same rows as {@link #exportTransactionsCsv}, as a JSON array for the live table preview. */
    public String queryTransactionsJson(TransactionFilter filter) throws SQLException {
        FilterSql f = buildFilterSql(filter);

        String sql = "SELECT timestamp_millis, type, player, owner, item_name, amount, price FROM transactions"
                + f.where + " ORDER BY timestamp_millis DESC LIMIT ?";
        f.params.add(filter.limit);

        StringBuilder json = new StringBuilder("[");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < f.params.size(); i++) {
                ps.setObject(i + 1, f.params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{\"timestamp\":").append(rs.getLong(1)).append(",")
                            .append("\"type\":").append(JsonUtil.quoteOrNull(rs.getString(2))).append(",")
                            .append("\"player\":").append(JsonUtil.quoteOrNull(rs.getString(3))).append(",")
                            .append("\"owner\":").append(JsonUtil.quoteOrNull(rs.getString(4))).append(",")
                            .append("\"item\":").append(JsonUtil.quoteOrNull(rs.getString(5))).append(",")
                            .append("\"amount\":").append(rs.getInt(6)).append(",")
                            .append("\"price\":").append(JsonUtil.money(rs.getDouble(7)))
                            .append("}");
                }
            }
        }
        json.append("]");
        return json.toString();
    }

    /** Aggregate stats for one player (as a customer) plus the shops they own, for the player profile page. */
    public String playerSummaryJson(String playerName) throws SQLException {
        int transactions = 0;
        double spent = 0;
        double earned = 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*), " +
                        "COALESCE(SUM(CASE WHEN type = 'BUY' THEN price ELSE 0 END), 0), " +
                        "COALESCE(SUM(CASE WHEN type = 'SELL' THEN price ELSE 0 END), 0) " +
                        "FROM transactions WHERE player = ? COLLATE NOCASE")) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    transactions = rs.getInt(1);
                    spent = rs.getDouble(2);
                    earned = rs.getDouble(3);
                }
            }
        }

        StringBuilder shops = new StringBuilder("[");
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT item_name, price, shop_buys, location FROM shops WHERE owner = ? COLLATE NOCASE ORDER BY item_name")) {
            ps.setString(1, playerName);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) shops.append(",");
                    first = false;
                    shops.append("{\"item\":").append(JsonUtil.quoteOrNull(rs.getString(1))).append(",")
                            .append("\"price\":").append(JsonUtil.money(rs.getDouble(2))).append(",")
                            .append("\"shopBuys\":").append(rs.getInt(3) == 1).append(",")
                            .append("\"location\":").append(JsonUtil.quoteOrNull(rs.getString(4)))
                            .append("}");
                }
            }
        }
        shops.append("]");

        return "{\"transactions\":" + transactions + ",\"spent\":" + JsonUtil.money(spent)
                + ",\"earned\":" + JsonUtil.money(earned) + ",\"shopsOwned\":" + shops + "}";
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
