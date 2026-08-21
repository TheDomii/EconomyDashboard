package com.thedomibusiness.economydashboard.economy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EconomySnapshot {

    public static class PlayerBalance {
        public final String name;
        public final double balance;

        public PlayerBalance(String name, double balance) {
            this.name = name;
            this.balance = balance;
        }
    }

    public final long generatedAtMillis;
    public final double totalMoney;
    public final int playerCount;
    public final List<PlayerBalance> topBalances;
    /** Every player with an account, unsorted-cap - used for CSV export/filtering. */
    public final List<PlayerBalance> allBalances;
    public final Map<String, Integer> distribution;

    public EconomySnapshot(long generatedAtMillis, double totalMoney, int playerCount,
                            List<PlayerBalance> topBalances, List<PlayerBalance> allBalances,
                            Map<String, Integer> distribution) {
        this.generatedAtMillis = generatedAtMillis;
        this.totalMoney = totalMoney;
        this.playerCount = playerCount;
        this.topBalances = topBalances;
        this.allBalances = allBalances;
        this.distribution = distribution;
    }

    public static EconomySnapshot empty() {
        return new EconomySnapshot(System.currentTimeMillis(), 0, 0,
                Collections.emptyList(), Collections.emptyList(), new LinkedHashMap<>());
    }
}
