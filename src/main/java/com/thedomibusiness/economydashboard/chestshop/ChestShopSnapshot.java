package com.thedomibusiness.economydashboard.chestshop;

import java.util.Collections;
import java.util.List;

public class ChestShopSnapshot {

    public static class ShopListing {
        public final String owner;
        public final String item;
        public final String quantity;
        public final Double buyPrice;
        public final Double sellPrice;
        public final String location;

        public ShopListing(String owner, String item, String quantity, Double buyPrice, Double sellPrice, String location) {
            this.owner = owner;
            this.item = item;
            this.quantity = quantity;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.location = location;
        }
    }

    public static class OwnerStats {
        public final String owner;
        public final int shopCount;
        public final int transactions;
        public final double revenue;
        public final double payouts;

        public OwnerStats(String owner, int shopCount, int transactions, double revenue, double payouts) {
            this.owner = owner;
            this.shopCount = shopCount;
            this.transactions = transactions;
            this.revenue = revenue;
            this.payouts = payouts;
        }

        public double net() {
            return revenue - payouts;
        }
    }

    public final long generatedAtMillis;
    public final int totalShops;
    public final int totalTransactions;
    public final double totalRevenue;
    public final double totalPayouts;
    public final List<OwnerStats> topOwners;
    public final List<ShopListing> shops;

    public ChestShopSnapshot(long generatedAtMillis, int totalShops, int totalTransactions,
                              double totalRevenue, double totalPayouts,
                              List<OwnerStats> topOwners, List<ShopListing> shops) {
        this.generatedAtMillis = generatedAtMillis;
        this.totalShops = totalShops;
        this.totalTransactions = totalTransactions;
        this.totalRevenue = totalRevenue;
        this.totalPayouts = totalPayouts;
        this.topOwners = topOwners;
        this.shops = shops;
    }

    public static ChestShopSnapshot empty() {
        return new ChestShopSnapshot(System.currentTimeMillis(), 0, 0, 0, 0,
                Collections.emptyList(), Collections.emptyList());
    }
}
