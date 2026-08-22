package com.thedomibusiness.economydashboard.presence;

import java.util.Collections;
import java.util.List;

/** Server population over time - daily unique players and the average online count per hour of day. */
public class PlayerActivitySnapshot {

    public static class DailyCount {
        public final String day;
        public final int uniquePlayers;

        public DailyCount(String day, int uniquePlayers) {
            this.day = day;
            this.uniquePlayers = uniquePlayers;
        }
    }

    public static class HourlyAverage {
        public final int hour;
        public final double avgOnline;

        public HourlyAverage(int hour, double avgOnline) {
            this.hour = hour;
            this.avgOnline = avgOnline;
        }
    }

    public final long generatedAtMillis;
    public final int currentOnline;
    public final int peakOnline;
    public final long peakOnlineAtMillis;
    public final List<DailyCount> dailyCounts;
    public final List<HourlyAverage> hourlyPattern;

    public PlayerActivitySnapshot(long generatedAtMillis, int currentOnline, int peakOnline, long peakOnlineAtMillis,
                                   List<DailyCount> dailyCounts, List<HourlyAverage> hourlyPattern) {
        this.generatedAtMillis = generatedAtMillis;
        this.currentOnline = currentOnline;
        this.peakOnline = peakOnline;
        this.peakOnlineAtMillis = peakOnlineAtMillis;
        this.dailyCounts = dailyCounts;
        this.hourlyPattern = hourlyPattern;
    }

    public static PlayerActivitySnapshot empty() {
        return new PlayerActivitySnapshot(System.currentTimeMillis(), 0, 0, 0,
                Collections.emptyList(), Collections.emptyList());
    }
}
