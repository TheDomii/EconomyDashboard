package com.thedomibusiness.economydashboard.chestshop;

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
 * Stores the ChestShop registry (built up live from ShopCreatedEvent /
 * ShopEditedEvent / ShopDestroyedEvent, since ChestShop itself has no "list all
 * shops" API) and a transaction history (from TransactionEvent). Persists across
 * restarts in getDataFolder()/chestshop.db - shops placed before this plugin was
 * installed only show up once they're next created/edited/traded at.
 */
public class ChestShopDatabase implements AutoCloseable {

    private final Connection connection;

    public ChestShopDatabase(File dbFile) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS shops (" +
                    "location TEXT PRIMARY KEY, " +
                    "owner TEXT NOT NULL, " +
                    "item_name TEXT, " +
                    "quantity TEXT, " +
                    "buy_price REAL, " +
                    "sell_price REAL, " +
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
            st.execute("CREATE INDEX IF NOT EXISTS idx_cs_owner ON shops(owner)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_cs_tx_owner ON transactions(owner)");
        }
    }

    public void upsertShop(String location, String owner, String itemName, String quantity,
                            Double buyPrice, Double sellPrice) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO shops(location, owner, item_name, quantity, buy_price, sell_price, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(location) DO UPDATE SET owner=excluded.owner, item_name=excluded.item_name, " +
                        "quantity=excluded.quantity, buy_price=excluded.buy_price, sell_price=excluded.sell_price, " +
                        "updated_at=excluded.updated_at")) {
            ps.setString(1, location);
            ps.setString(2, owner);
            ps.setString(3, itemName);
            ps.setString(4, quantity);
            setNullableDouble(ps, 5, buyPrice);
            setNullableDouble(ps, 6, sellPrice);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    public void removeShop(String location) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM shops WHERE location = ?")) {
            ps.setString(1, location);
            ps.executeUpdate();
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

    public ChestShopSnapshot snapshot(int shopListLimit, int topOwnersLimit) throws SQLException {
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

        List<ChestShopSnapshot.OwnerStats> topOwners = new ArrayList<>();
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
                topOwners.add(new ChestShopSnapshot.OwnerStats(rs.getString(1), rs.getInt(2), rs.getInt(3),
                        rs.getDouble(4), rs.getDouble(5)));
            }
        }

        List<ChestShopSnapshot.ShopListing> shops = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT owner, item_name, quantity, buy_price, sell_price, location FROM shops " +
                             "ORDER BY owner, item_name LIMIT " + shopListLimit)) {
            while (rs.next()) {
                Double buy = rs.getObject(4) != null ? rs.getDouble(4) : null;
                Double sell = rs.getObject(5) != null ? rs.getDouble(5) : null;
                shops.add(new ChestShopSnapshot.ShopListing(rs.getString(1), rs.getString(2), rs.getString(3),
                        buy, sell, rs.getString(6)));
            }
        }

        return new ChestShopSnapshot(System.currentTimeMillis(), totalShops, totalTransactions,
                totalRevenue, totalPayouts, topOwners, shops);
    }

    public List<ChestShopSnapshot.ShopListing> searchShops(String query, int limit) throws SQLException {
        List<ChestShopSnapshot.ShopListing> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT owner, item_name, quantity, buy_price, sell_price, location FROM shops " +
                        "WHERE owner LIKE ? COLLATE NOCASE OR item_name LIKE ? COLLATE NOCASE " +
                        "ORDER BY owner LIMIT ?")) {
            ps.setString(1, "%" + query + "%");
            ps.setString(2, "%" + query + "%");
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Double buy = rs.getObject(4) != null ? rs.getDouble(4) : null;
                    Double sell = rs.getObject(5) != null ? rs.getDouble(5) : null;
                    result.add(new ChestShopSnapshot.ShopListing(rs.getString(1), rs.getString(2), rs.getString(3),
                            buy, sell, rs.getString(6)));
                }
            }
        }
        return result;
    }

    private void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value != null) {
            ps.setDouble(index, value);
        } else {
            ps.setNull(index, java.sql.Types.REAL);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
