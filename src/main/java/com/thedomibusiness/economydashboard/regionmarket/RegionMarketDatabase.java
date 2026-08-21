package com.thedomibusiness.economydashboard.regionmarket;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors AdvancedRegionMarket's own region list (refreshed periodically via
 * RegionMarketCollector#collect()), same idea as QuickShopDatabase. There is no
 * reliable "region sold" event in ARM's API (see RegionMarketCollector), so
 * transactions here are detected by diffing each poll's sold-state against the
 * previous poll: false -> true is recorded as a SELL, true -> false as an UNSELL.
 * The timestamp is therefore "first noticed by this plugin", not the exact moment
 * of purchase - accurate to within one refresh interval.
 */
public class RegionMarketDatabase implements AutoCloseable {

    private final Connection connection;

    public RegionMarketDatabase(File dbFile) throws SQLException {
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS regions (" +
                    "region_key TEXT PRIMARY KEY, " +
                    "region_id TEXT NOT NULL, " +
                    "world TEXT NOT NULL, " +
                    "region_kind TEXT, " +
                    "sell_type TEXT, " +
                    "sold INTEGER NOT NULL DEFAULT 0, " +
                    "owner TEXT, " +
                    "price REAL NOT NULL DEFAULT 0, " +
                    "subregion INTEGER NOT NULL DEFAULT 0, " +
                    "updated_at INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp_millis INTEGER NOT NULL, " +
                    "type TEXT NOT NULL, " +
                    "region_id TEXT NOT NULL, " +
                    "world TEXT, " +
                    "player TEXT, " +
                    "owner TEXT, " +
                    "price REAL NOT NULL DEFAULT 0)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_rm_owner ON regions(owner)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_rm_tx_owner ON transactions(owner)");
        }
    }

    /**
     * Replaces the whole region registry with the given list, recording a SELL/UNSELL
     * transaction for every sold-state transition detected against the previous poll.
     * On the very first poll (empty table) no transactions are recorded, so an
     * already-sold server doesn't get flooded with fake "just sold" rows at startup.
     */
    public void replaceRegions(List<RegionMarketSnapshot.RegionListing> regions) throws SQLException {
        Map<String, RegionMarketSnapshot.RegionListing> previous = new HashMap<>();
        boolean firstPoll = true;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT region_key, region_id, world, sold, owner, price FROM regions")) {
            while (rs.next()) {
                firstPoll = false;
                previous.put(rs.getString(1), new RegionMarketSnapshot.RegionListing(
                        rs.getString(2), rs.getString(3), null, null,
                        rs.getInt(4) == 1, rs.getString(5), rs.getDouble(6), false));
            }
        }

        if (!firstPoll) {
            for (RegionMarketSnapshot.RegionListing current : regions) {
                RegionMarketSnapshot.RegionListing before = previous.get(regionKey(current));
                if (before == null) {
                    continue;
                }
                if (!before.sold && current.sold) {
                    insertTransaction("SELL", current.regionId, current.world, current.owner, current.owner, current.price);
                } else if (before.sold && !current.sold) {
                    insertTransaction("UNSELL", current.regionId, current.world, before.owner, before.owner, before.price);
                } else if (before.sold && current.sold
                        && before.owner != null && current.owner != null && !before.owner.equals(current.owner)) {
                    insertTransaction("SELL", current.regionId, current.world, current.owner, current.owner, current.price);
                }
            }
        }

        connection.setAutoCommit(false);
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM regions");
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO regions(region_key, region_id, world, region_kind, sell_type, sold, owner, price, subregion, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            long now = System.currentTimeMillis();
            for (RegionMarketSnapshot.RegionListing r : regions) {
                ps.setString(1, regionKey(r));
                ps.setString(2, r.regionId);
                ps.setString(3, r.world);
                ps.setString(4, r.regionKind);
                ps.setString(5, r.sellType);
                ps.setInt(6, r.sold ? 1 : 0);
                ps.setString(7, r.owner);
                ps.setDouble(8, r.price);
                ps.setInt(9, r.subregion ? 1 : 0);
                ps.setLong(10, now);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        connection.commit();
        connection.setAutoCommit(true);
    }

    private String regionKey(RegionMarketSnapshot.RegionListing r) {
        return r.world + ":" + r.regionId;
    }

    private void insertTransaction(String type, String regionId, String world, String player, String owner,
                                    double price) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO transactions(timestamp_millis, type, region_id, world, player, owner, price) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.setString(2, type);
            ps.setString(3, regionId);
            ps.setString(4, world);
            ps.setString(5, player);
            ps.setString(6, owner);
            ps.setDouble(7, price);
            ps.executeUpdate();
        }
    }

    public RegionMarketSnapshot snapshot(int regionListLimit, int topOwnersLimit) throws SQLException {
        List<RegionMarketSnapshot.RegionListing> regions = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT region_id, world, region_kind, sell_type, sold, owner, price, subregion FROM regions " +
                             "ORDER BY world, region_id LIMIT " + regionListLimit)) {
            while (rs.next()) {
                regions.add(new RegionMarketSnapshot.RegionListing(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getInt(5) == 1, rs.getString(6), rs.getDouble(7), rs.getInt(8) == 1));
            }
        }

        int totalRegions = 0;
        int soldRegions = 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*), COALESCE(SUM(sold), 0) FROM regions")) {
            if (rs.next()) {
                totalRegions = rs.getInt(1);
                soldRegions = rs.getInt(2);
            }
        }

        int totalSales = 0;
        double totalSalesVolume = 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*), COALESCE(SUM(price), 0) FROM transactions WHERE type = 'SELL'")) {
            if (rs.next()) {
                totalSales = rs.getInt(1);
                totalSalesVolume = rs.getDouble(2);
            }
        }

        List<RegionMarketSnapshot.OwnerStats> topOwners = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT owner, " +
                             "(SELECT COUNT(*) FROM regions r2 WHERE r2.owner = t.owner) AS region_count, " +
                             "COUNT(*) AS purchases, " +
                             "COALESCE(SUM(price), 0) AS total_spent " +
                             "FROM transactions t WHERE owner IS NOT NULL AND type = 'SELL' " +
                             "GROUP BY owner ORDER BY total_spent DESC LIMIT " + topOwnersLimit)) {
            while (rs.next()) {
                topOwners.add(new RegionMarketSnapshot.OwnerStats(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getDouble(4)));
            }
        }

        return new RegionMarketSnapshot(System.currentTimeMillis(), totalRegions, soldRegions, totalRegions - soldRegions,
                totalSales, totalSalesVolume, topOwners, regions);
    }

    public List<RegionMarketSnapshot.RegionListing> searchRegions(String query, int limit) throws SQLException {
        List<RegionMarketSnapshot.RegionListing> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT region_id, world, region_kind, sell_type, sold, owner, price, subregion FROM regions " +
                        "WHERE region_id LIKE ? COLLATE NOCASE OR owner LIKE ? COLLATE NOCASE " +
                        "ORDER BY region_id LIMIT ?")) {
            ps.setString(1, "%" + query + "%");
            ps.setString(2, "%" + query + "%");
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new RegionMarketSnapshot.RegionListing(rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getInt(5) == 1, rs.getString(6), rs.getDouble(7), rs.getInt(8) == 1));
                }
            }
        }
        return result;
    }

    /** Current region registry as CSV, with optional owner/world/sold substring/exact filters. */
    public String exportRegionsCsv(String owner, String world, Boolean sold) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (owner != null && !owner.isEmpty()) {
            where.append(" AND owner LIKE ? COLLATE NOCASE");
            params.add("%" + owner + "%");
        }
        if (world != null && !world.isEmpty()) {
            where.append(" AND world LIKE ? COLLATE NOCASE");
            params.add("%" + world + "%");
        }
        if (sold != null) {
            where.append(" AND sold = ?");
            params.add(sold ? 1 : 0);
        }

        CsvBuilder csv = new CsvBuilder();
        csv.header("region_id", "world", "region_kind", "sell_type", "sold", "owner", "price", "subregion", "updated_at");
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT region_id, world, region_kind, sell_type, sold, owner, price, subregion, updated_at FROM regions"
                        + where + " ORDER BY world, region_id")) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    csv.row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getInt(5) == 1, rs.getString(6), CsvBuilder.formatMoney(rs.getDouble(7)),
                            rs.getInt(8) == 1, CsvBuilder.formatTimestamp(rs.getLong(9)));
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
            where.append(" AND owner LIKE ? COLLATE NOCASE");
            params.add("%" + filter.player + "%");
        }
        if (filter.counterparty != null) {
            where.append(" AND region_id LIKE ? COLLATE NOCASE");
            params.add("%" + filter.counterparty + "%");
        }
        if (filter.minPrice != null) {
            where.append(" AND price >= ?");
            params.add(filter.minPrice);
        }
        if (filter.maxPrice != null) {
            where.append(" AND price <= ?");
            params.add(filter.maxPrice);
        }

        String sql = "SELECT timestamp_millis, type, region_id, world, owner, price FROM transactions"
                + where + " ORDER BY timestamp_millis DESC LIMIT ?";
        params.add(filter.limit);

        CsvBuilder csv = new CsvBuilder();
        csv.header("timestamp", "type", "region_id", "world", "owner", "price");
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    csv.row(CsvBuilder.formatTimestamp(rs.getLong(1)), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), CsvBuilder.formatMoney(rs.getDouble(6)));
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
