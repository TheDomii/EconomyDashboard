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

    public static class NationStats {
        public final String name;
        public final String capital;
        public final int townCount;
        public final int residentCount;
        public final double totalBalance;

        public NationStats(String name, String capital, int townCount, int residentCount, double totalBalance) {
            this.name = name;
            this.capital = capital;
            this.townCount = townCount;
            this.residentCount = residentCount;
            this.totalBalance = totalBalance;
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
    /** Every town, unsorted-cap - used for CSV export/filtering. */
    public final List<TownStats> allTowns;
    /** Every nation, sorted by total member-town balance descending. */
    public final List<NationStats> nations;

    public TownySnapshot(long generatedAtMillis, int totalTowns, double totalTownBalance,
                          int newTownsLast24h, int newTownsLast7d, int newPlotsLast24h, int newPlotsLast7d,
                          List<TownStats> topTowns, List<TownStats> allTowns, List<NationStats> nations) {
        this.generatedAtMillis = generatedAtMillis;
        this.totalTowns = totalTowns;
        this.totalTownBalance = totalTownBalance;
        this.newTownsLast24h = newTownsLast24h;
        this.newTownsLast7d = newTownsLast7d;
        this.newPlotsLast24h = newPlotsLast24h;
        this.newPlotsLast7d = newPlotsLast7d;
        this.topTowns = topTowns;
        this.allTowns = allTowns;
        this.nations = nations;
    }

    public static TownySnapshot empty() {
        return new TownySnapshot(System.currentTimeMillis(), 0, 0, 0, 0, 0, 0,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }
}
