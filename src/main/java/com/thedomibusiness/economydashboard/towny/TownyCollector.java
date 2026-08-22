package com.thedomibusiness.economydashboard.towny;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import com.palmergames.bukkit.towny.object.TownBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        Map<String, Integer> nationTownCount = new LinkedHashMap<>();
        Map<String, Integer> nationResidentCount = new LinkedHashMap<>();
        Map<String, Double> nationBalance = new LinkedHashMap<>();
        Map<String, String> nationCapital = new LinkedHashMap<>();

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

                String nationName = null;
                if (town.hasNation()) {
                    Nation nation = town.getNationOrNull();
                    nationName = nation.getName();
                    nationTownCount.merge(nationName, 1, Integer::sum);
                    nationResidentCount.merge(nationName, town.getResidents().size(), Integer::sum);
                    nationBalance.merge(nationName, balance, Double::sum);
                    if (!nationCapital.containsKey(nationName)) {
                        try {
                            nationCapital.put(nationName, nation.getCapital().getName());
                        } catch (Exception e) {
                            nationCapital.put(nationName, null);
                        }
                    }
                }
                townStats.add(new TownySnapshot.TownStats(town.getName(), balance, plotCount, nationName));
            } catch (Exception ignored) {
                // one broken/mid-deletion town shouldn't kill the whole snapshot
            }
        }

        townStats.sort(Comparator.comparingDouble((TownySnapshot.TownStats t) -> t.balance).reversed());
        List<TownySnapshot.TownStats> topTowns = new ArrayList<>(townStats.subList(0, Math.min(10, townStats.size())));

        List<TownySnapshot.NationStats> nationStats = new ArrayList<>();
        for (String nationName : nationTownCount.keySet()) {
            nationStats.add(new TownySnapshot.NationStats(nationName, nationCapital.get(nationName),
                    nationTownCount.get(nationName), nationResidentCount.get(nationName), nationBalance.get(nationName)));
        }
        nationStats.sort(Comparator.comparingDouble((TownySnapshot.NationStats n) -> n.totalBalance).reversed());

        return new TownySnapshot(now, totalTowns, totalBalance, newTowns24h, newTowns7d,
                newPlots24h, newPlots7d, topTowns, townStats, nationStats);
    }

    /** Live per-player lookup for the player profile page. Returns null if the name is unknown to Towny. */
    public TownyResidentSummary lookupResident(String playerName) {
        Resident resident = TownyAPI.getInstance().getResident(playerName);
        if (resident == null) {
            return null;
        }
        try {
            String townName = resident.hasTown() ? resident.getTownOrNull().getName() : null;
            String nationName = resident.hasNation() ? resident.getNationOrNull().getName() : null;

            int ownedPlots = 0;
            if (resident.hasTown()) {
                for (TownBlock block : resident.getTownOrNull().getTownBlocks()) {
                    if (resident.equals(block.getResidentOrNull())) {
                        ownedPlots++;
                    }
                }
            }

            return new TownyResidentSummary(townName, nationName, resident.getJoinedTownAt(),
                    resident.getRegistered(), ownedPlots);
        } catch (Exception e) {
            return null;
        }
    }
}
