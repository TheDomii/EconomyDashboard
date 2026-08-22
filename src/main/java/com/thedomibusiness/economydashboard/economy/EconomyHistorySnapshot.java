package com.thedomibusiness.economydashboard.economy;

import java.util.Collections;
import java.util.List;

/** Daily money-supply trend, same idea as PlayerActivitySnapshot but for the economy module. */
public class EconomyHistorySnapshot {

    public static class DailyMoneyPoint {
        public final String day;
        public final double avgMoney;
        public final int maxPlayers;

        public DailyMoneyPoint(String day, double avgMoney, int maxPlayers) {
            this.day = day;
            this.avgMoney = avgMoney;
            this.maxPlayers = maxPlayers;
        }
    }

    public final long generatedAtMillis;
    /** Newest first, like PlayerActivitySnapshot.dailyCounts. */
    public final List<DailyMoneyPoint> dailyPoints;

    public EconomyHistorySnapshot(long generatedAtMillis, List<DailyMoneyPoint> dailyPoints) {
        this.generatedAtMillis = generatedAtMillis;
        this.dailyPoints = dailyPoints;
    }

    public static EconomyHistorySnapshot empty() {
        return new EconomyHistorySnapshot(System.currentTimeMillis(), Collections.emptyList());
    }
}
