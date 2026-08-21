package com.thedomibusiness.economydashboard.search;

import com.thedomibusiness.economydashboard.chestshop.ChestShopService;
import com.thedomibusiness.economydashboard.chestshop.ChestShopSnapshot;
import com.thedomibusiness.economydashboard.quickshop.QuickShopService;
import com.thedomibusiness.economydashboard.quickshop.QuickShopSnapshot;
import com.thedomibusiness.economydashboard.traders.DtlTradersLogCollector;
import com.thedomibusiness.economydashboard.traders.ShopPriceEntry;
import com.thedomibusiness.economydashboard.traders.TraderSnapshot;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Live, on-demand search across players, shops and items. Not indexed - each call
 * re-scans the current data (Bukkit's offline players, the trader database, and the
 * live shop price list), which is fine at the scale of a single server's economy.
 */
public class SearchService {

    private static final int DEFAULT_LIMIT = 20;

    private final Plugin plugin;
    private final Economy economy;
    private final DtlTradersLogCollector tradersCollector;
    private final Supplier<List<ShopPriceEntry>> pricesSupplier;
    private final ChestShopService chestShopService;
    private final QuickShopService quickShopService;

    public SearchService(Plugin plugin, Economy economy, DtlTradersLogCollector tradersCollector,
                          Supplier<List<ShopPriceEntry>> pricesSupplier, ChestShopService chestShopService,
                          QuickShopService quickShopService) {
        this.plugin = plugin;
        this.economy = economy;
        this.tradersCollector = tradersCollector;
        this.pricesSupplier = pricesSupplier;
        this.chestShopService = chestShopService;
        this.quickShopService = quickShopService;
    }

    public SearchResult search(String query) {
        String needle = query.toLowerCase(Locale.ROOT);

        List<SearchResult.PlayerResult> players = searchPlayers(needle);
        List<TraderSnapshot.ShopStats> shops = tradersCollector != null
                ? tradersCollector.searchShops(query, DEFAULT_LIMIT)
                : Collections.emptyList();
        List<SearchResult.ItemResult> items = searchItems(query, needle);
        List<ChestShopSnapshot.ShopListing> chestShops = chestShopService != null
                ? chestShopService.searchShops(query, DEFAULT_LIMIT)
                : Collections.emptyList();
        List<QuickShopSnapshot.ShopListing> quickShops = quickShopService != null
                ? quickShopService.searchShops(query, DEFAULT_LIMIT)
                : Collections.emptyList();

        return new SearchResult(players, shops, items, chestShops, quickShops);
    }

    private List<SearchResult.PlayerResult> searchPlayers(String needle) {
        List<SearchResult.PlayerResult> results = new ArrayList<>();
        for (OfflinePlayer player : plugin.getServer().getOfflinePlayers()) {
            if (results.size() >= DEFAULT_LIMIT) {
                break;
            }
            String name = player.getName();
            if (name == null || !name.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            if (!economy.hasAccount(player)) {
                continue;
            }
            results.add(new SearchResult.PlayerResult(name, economy.getBalance(player)));
        }
        return results;
    }

    private List<SearchResult.ItemResult> searchItems(String query, String needle) {
        Map<String, Double> currentBuyPrices = new LinkedHashMap<>();
        Map<String, Double> currentSellPrices = new LinkedHashMap<>();
        for (ShopPriceEntry entry : pricesSupplier.get()) {
            if (!entry.itemName.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            if (entry.buyPrice != null) {
                currentBuyPrices.put(entry.itemName, entry.buyPrice);
            }
            if (entry.sellPrice != null) {
                currentSellPrices.put(entry.itemName, entry.sellPrice);
            }
        }

        Map<String, TraderSnapshot.ItemStats> historyByName = new LinkedHashMap<>();
        if (tradersCollector != null) {
            for (TraderSnapshot.ItemStats stats : tradersCollector.searchItems(query, DEFAULT_LIMIT)) {
                historyByName.put(stats.name, stats);
            }
        }

        Map<String, SearchResult.ItemResult> merged = new LinkedHashMap<>();
        for (Map.Entry<String, TraderSnapshot.ItemStats> e : historyByName.entrySet()) {
            merged.put(e.getKey(), new SearchResult.ItemResult(e.getKey(), e.getValue().boughtQty, e.getValue().soldQty,
                    currentBuyPrices.get(e.getKey()), currentSellPrices.get(e.getKey())));
        }
        for (String name : currentBuyPrices.keySet()) {
            merged.putIfAbsent(name, new SearchResult.ItemResult(name, 0, 0, currentBuyPrices.get(name), currentSellPrices.get(name)));
        }
        for (String name : currentSellPrices.keySet()) {
            merged.putIfAbsent(name, new SearchResult.ItemResult(name, 0, 0, currentBuyPrices.get(name), currentSellPrices.get(name)));
        }

        List<SearchResult.ItemResult> result = new ArrayList<>(merged.values());
        if (result.size() > DEFAULT_LIMIT) {
            result = result.subList(0, DEFAULT_LIMIT);
        }
        return result;
    }
}
