package com.thedomibusiness.economydashboard.traders;

import java.util.Collections;
import java.util.List;

public class TraderSnapshot {

    public static class ShopStats {
        public final String name;
        public final int transactions;
        public final double revenue;
        public final double payouts;

        public ShopStats(String name, int transactions, double revenue, double payouts) {
            this.name = name;
            this.transactions = transactions;
            this.revenue = revenue;
            this.payouts = payouts;
        }

        public double net() {
            return revenue - payouts;
        }
    }

    public static class ItemStats {
        public final String name;
        public final int boughtQty;
        public final int soldQty;

        public ItemStats(String name, int boughtQty, int soldQty) {
            this.name = name;
            this.boughtQty = boughtQty;
            this.soldQty = soldQty;
        }
    }

    public final long generatedAtMillis;
    public final int totalTransactions;
    /** Money that flowed into shops (players buying from the shop). */
    public final double totalRevenue;
    /** Money that flowed out of shops (players selling to the shop). */
    public final double totalPayouts;
    /**
     * Sum of "extra price" amounts on TRADE transactions. dtlTradersPlus doesn't
     * record which side paid it, so this is kept separate rather than folded into
     * revenue/payouts.
     */
    public final double totalTradeVolume;
    public final List<ShopStats> topShops;
    public final List<ItemStats> topItems;

    public TraderSnapshot(long generatedAtMillis, int totalTransactions, double totalRevenue, double totalPayouts,
                           double totalTradeVolume, List<ShopStats> topShops, List<ItemStats> topItems) {
        this.generatedAtMillis = generatedAtMillis;
        this.totalTransactions = totalTransactions;
        this.totalRevenue = totalRevenue;
        this.totalPayouts = totalPayouts;
        this.totalTradeVolume = totalTradeVolume;
        this.topShops = topShops;
        this.topItems = topItems;
    }

    public double totalNet() {
        return totalRevenue - totalPayouts;
    }

    public static TraderSnapshot empty() {
        return new TraderSnapshot(System.currentTimeMillis(), 0, 0, 0, 0, Collections.emptyList(), Collections.emptyList());
    }
}
