package com.thedomibusiness.economydashboard.traders;

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
        // setAutoCommit(true) must run even if rollback() itself throws (e.g. a failed write
        // already made SQLite drop the transaction, so there's nothing left to roll back) -
        // otherwise the connection stays stuck in manual-commit mode forever. See
        // QuickShopDatabase#replaceShops for the incident that surfaced this exact bug pattern.
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
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
            ps.setLong(1, tx.timestampMillis);
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
                             "FROM transactions GROUP BY shop ORDER BY (revenue - payouts) DESC LIMIT 500")) {
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
                             "GROUP BY item_name ORDER BY (bought + sold) DESC LIMIT 500")) {
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
            f.where.append(" AND shop LIKE ? COLLATE NOCASE");
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

    /**
     * Raw (non-aggregated) transaction rows for CSV export/external analysis,
     * with optional filters. Newest first.
     */
    public String exportTransactionsCsv(TransactionFilter filter) throws SQLException {
        FilterSql f = buildFilterSql(filter);

        String sql = "SELECT timestamp_millis, type, player, shop, page, item_name, item_amount, price FROM transactions"
                + f.where + " ORDER BY timestamp_millis DESC LIMIT ?";
        f.params.add(filter.limit);

        CsvBuilder csv = new CsvBuilder();
        csv.header("timestamp", "type", "player", "shop", "page", "item", "amount", "price");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < f.params.size(); i++) {
                ps.setObject(i + 1, f.params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    csv.row(CsvBuilder.formatTimestamp(rs.getLong(1)), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7),
                            CsvBuilder.formatMoney(rs.getDouble(8)));
                }
            }
        }
        return csv.build();
    }

    /**
     * Same rows as {@link #exportTransactionsCsv}, as a JSON array - for the live, filtered
     * table preview on the dashboard page (the CSV export stays the "download everything
     * matching" path with its own, usually much higher, limit).
     */
    public String queryTransactionsJson(TransactionFilter filter) throws SQLException {
        FilterSql f = buildFilterSql(filter);

        String sql = "SELECT timestamp_millis, type, player, shop, page, item_name, item_amount, price FROM transactions"
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
                            .append("\"shop\":").append(JsonUtil.quoteOrNull(rs.getString(4))).append(",")
                            .append("\"page\":").append(JsonUtil.quoteOrNull(rs.getString(5))).append(",")
                            .append("\"item\":").append(JsonUtil.quoteOrNull(rs.getString(6))).append(",")
                            .append("\"amount\":").append(rs.getInt(7)).append(",")
                            .append("\"price\":").append(JsonUtil.money(rs.getDouble(8)))
                            .append("}");
                }
            }
        }
        json.append("]");
        return json.toString();
    }

    /** Most recent transactions as human-readable activity lines, newest first - for the "Live-Aktivität" feed. */
    public List<com.thedomibusiness.economydashboard.activity.ActivityEvent> recentActivity(int limit) throws SQLException {
        List<com.thedomibusiness.economydashboard.activity.ActivityEvent> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT timestamp_millis, type, player, shop, item_name, item_amount, price FROM transactions " +
                        "ORDER BY timestamp_millis DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long ts = rs.getLong(1);
                    String type = rs.getString(2);
                    String player = rs.getString(3);
                    String shop = rs.getString(4);
                    String item = rs.getString(5);
                    int amount = rs.getInt(6);
                    double price = rs.getDouble(7);
                    String desc;
                    if ("BUY".equals(type)) {
                        desc = player + " kaufte " + (item != null ? amount + "x " + item : "etwas") + " bei " + shop + " fuer " + JsonUtil.money(price);
                    } else if ("SELL".equals(type)) {
                        desc = player + " verkaufte " + (item != null ? amount + "x " + item : "etwas") + " an " + shop + " fuer " + JsonUtil.money(price);
                    } else {
                        desc = player + " handelte bei " + shop + " fuer " + JsonUtil.money(price);
                    }
                    result.add(new com.thedomibusiness.economydashboard.activity.ActivityEvent(ts, "dtlTradersPlus", player, desc));
                }
            }
        }
        return result;
    }

    /** One row per (item, player) with their total sold quantity - raw material for anomaly detection. */
    public List<com.thedomibusiness.economydashboard.anomaly.SellVolumeRow> sellVolumeByItemAndPlayer() throws SQLException {
        List<com.thedomibusiness.economydashboard.anomaly.SellVolumeRow> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT item_name, player, SUM(item_amount) FROM transactions " +
                             "WHERE type = 'SELL' AND item_name IS NOT NULL AND player IS NOT NULL " +
                             "GROUP BY item_name, player")) {
            while (rs.next()) {
                result.add(new com.thedomibusiness.economydashboard.anomaly.SellVolumeRow(
                        rs.getString(1), rs.getString(2), rs.getLong(3), "dtlTradersPlus"));
            }
        }
        return result;
    }

    /** Aggregate stats for one player, for the cross-module player profile page. */
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
        return "{\"transactions\":" + transactions + ",\"spent\":" + JsonUtil.money(spent)
                + ",\"earned\":" + JsonUtil.money(earned) + "}";
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
