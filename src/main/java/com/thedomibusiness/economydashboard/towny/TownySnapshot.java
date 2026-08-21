package com.thedomibusiness.economydashboard.towny;

import java.util.Collections;
import java.util.List;

public class TownySnapshot {

    public static class TownStats {
        public final String name;
        public final double balance;
        public final int plots;
        public final String nation;

        public TownStats(String name, double balance, int plots, String nation) {
            this.name = name;
            this.balance = balance;
            this.plots = plots;
            this.nation = nation;
        }
    }

    public final long generatedAtMillis;
    public final int totalTowns;
    public final double totalTownBalance;
    public final int newTownsLast24h;
    public final int newTownsLast7d;
    public final int newPlotsLast24h;
    public final int newPlotsLast7d;
    public final List<TownStats> topTowns;

    public TownySnapshot(long generatedAtMillis, int totalTowns, double totalTownBalance,
                          int newTownsLast24h, int newTownsLast7d, int newPlotsLast24h, int newPlotsLast7d,
                          List<TownStats> topTowns) {
        this.generatedAtMillis = generatedAtMillis;
        this.totalTowns = totalTowns;
        this.totalTownBalance = totalTownBalance;
        this.newTownsLast24h = newTownsLast24h;
        this.newTownsLast7d = newTownsLast7d;
        this.newPlotsLast24h = newPlotsLast24h;
        this.newPlotsLast7d = newPlotsLast7d;
        this.topTowns = topTowns;
    }

    public static TownySnapshot empty() {
        return new TownySnapshot(System.currentTimeMillis(), 0, 0, 0, 0, 0, 0, Collections.emptyList());
    }
}
