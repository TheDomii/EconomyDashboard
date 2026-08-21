package com.thedomibusiness.economydashboard.towny;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads live data straight from Towny's own in-memory objects via TownyAPI - no
 * separate storage of our own. Only call this while Towny is actually enabled
 * (checked by the caller before this class is even instantiated), and only from
 * the main thread since it walks another plugin's live objects.
 */
public class TownyCollector {

    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;
    private static final long WEEK_MILLIS = 7 * DAY_MILLIS;

    public TownySnapshot collect() {
        List<Town> towns = TownyAPI.getInstance().getTowns();
        long now = System.currentTimeMillis();

        int totalTowns = 0;
        double totalBalance = 0;
        int newTowns24h = 0;
        int newTowns7d = 0;
        int newPlots24h = 0;
        int newPlots7d = 0;
        List<TownySnapshot.TownStats> townStats = new ArrayList<>();

        for (Town town : towns) {
            try {
                totalTowns++;
                double balance = town.getAccount().getHoldingBalance();
                totalBalance += balance;

                long registered = town.getRegistered();
                if (now - registered <= DAY_MILLIS) {
                    newTowns24h++;
                }
                if (now - registered <= WEEK_MILLIS) {
                    newTowns7d++;
                }

                int plotCount = 0;
                for (TownBlock block : town.getTownBlocks()) {
                    plotCount++;
                    long claimedAt = block.getClaimedAt();
                    if (claimedAt <= 0) {
                        continue;
                    }
                    if (now - claimedAt <= DAY_MILLIS) {
                        newPlots24h++;
                    }
                    if (now - claimedAt <= WEEK_MILLIS) {
                        newPlots7d++;
                    }
                }

                String nation = town.hasNation() ? town.getNationOrNull().getName() : null;
                townStats.add(new TownySnapshot.TownStats(town.getName(), balance, plotCount, nation));
            } catch (Exception ignored) {
                // one broken/mid-deletion town shouldn't kill the whole snapshot
            }
        }

        townStats.sort(Comparator.comparingDouble((TownySnapshot.TownStats t) -> t.balance).reversed());
        List<TownySnapshot.TownStats> topTowns = new ArrayList<>(townStats.subList(0, Math.min(10, townStats.size())));

        return new TownySnapshot(now, totalTowns, totalBalance, newTowns24h, newTowns7d,
                newPlots24h, newPlots7d, topTowns);
    }
}
