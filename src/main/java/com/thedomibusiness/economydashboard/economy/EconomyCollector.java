package com.thedomibusiness.economydashboard.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads player balances through Vault, so it works with whatever economy
 * plugin is registered (TheNewEconomy) without touching its storage directly.
 */
public class EconomyCollector {

    private static final double[] BUCKET_UPPER_BOUNDS = {100, 1_000, 10_000, 100_000, 1_000_000};

    private final Plugin plugin;
    private final Economy economy;

    public EconomyCollector(Plugin plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public EconomySnapshot collect() {
        OfflinePlayer[] offlinePlayers = plugin.getServer().getOfflinePlayers();

        double totalMoney = 0;
        int playerCount = 0;
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (double bound : BUCKET_UPPER_BOUNDS) {
            distribution.put(bucketLabel(bound), 0);
        }
        distribution.put("mehr", 0);

        List<EconomySnapshot.PlayerBalance> balances = new ArrayList<>();

        for (OfflinePlayer player : offlinePlayers) {
            if (!economy.hasAccount(player)) {
                continue;
            }
            double balance;
            try {
                balance = economy.getBalance(player);
            } catch (Exception e) {
                continue;
            }

            totalMoney += balance;
            playerCount++;
            String name = player.getName() != null ? player.getName() : player.getUniqueId().toString();
            balances.add(new EconomySnapshot.PlayerBalance(name, balance));

            String bucket = bucketFor(balance);
            distribution.merge(bucket, 1, Integer::sum);
        }

        balances.sort(Comparator.comparingDouble((EconomySnapshot.PlayerBalance b) -> b.balance).reversed());
        List<EconomySnapshot.PlayerBalance> top = new ArrayList<>(balances.subList(0, Math.min(10, balances.size())));

        return new EconomySnapshot(System.currentTimeMillis(), totalMoney, playerCount,
                top, balances, distribution);
    }

    private String bucketFor(double balance) {
        for (double bound : BUCKET_UPPER_BOUNDS) {
            if (balance < bound) {
                return bucketLabel(bound);
            }
        }
        return "mehr";
    }

    private String bucketLabel(double upperBound) {
        return "< " + (long) upperBound;
    }
}
