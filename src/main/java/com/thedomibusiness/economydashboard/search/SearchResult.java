package com.thedomibusiness.economydashboard.search;

import com.thedomibusiness.economydashboard.quickshop.QuickShopSnapshot;
import com.thedomibusiness.economydashboard.regionmarket.RegionMarketSnapshot;
import com.thedomibusiness.economydashboard.traders.TraderSnapshot;

import java.util.List;

public class SearchResult {

    public static class PlayerResult {
        public final String name;
        public final double balance;

        public PlayerResult(String name, double balance) {
            this.name = name;
            this.balance = balance;
        }
    }

    public static class ItemResult {
        public final String name;
        public final int boughtQty;
        public final int soldQty;
        public final Double buyPrice;
        public final Double sellPrice;

        public ItemResult(String name, int boughtQty, int soldQty, Double buyPrice, Double sellPrice) {
            this.name = name;
            this.boughtQty = boughtQty;
            this.soldQty = soldQty;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
        }
    }

    public final List<PlayerResult> players;
    public final List<TraderSnapshot.ShopStats> shops;
    public final List<ItemResult> items;
    public final List<QuickShopSnapshot.ShopListing> quickShops;
    public final List<RegionMarketSnapshot.RegionListing> regions;

    public SearchResult(List<PlayerResult> players, List<TraderSnapshot.ShopStats> shops, List<ItemResult> items,
                         List<QuickShopSnapshot.ShopListing> quickShops, List<RegionMarketSnapshot.RegionListing> regions) {
        this.players = players;
        this.shops = shops;
        this.items = items;
        this.quickShops = quickShops;
        this.regions = regions;
    }
}
