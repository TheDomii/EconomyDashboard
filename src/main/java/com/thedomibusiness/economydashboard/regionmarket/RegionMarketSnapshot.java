package com.thedomibusiness.economydashboard.regionmarket;

import java.util.Collections;
import java.util.List;

public class RegionMarketSnapshot {

    public static class RegionListing {
        public final String regionId;
        public final String world;
        public final String regionKind;
        public final String sellType;
        public final boolean sold;
        public final String owner;
        public final double price;
        public final boolean subregion;

        public RegionListing(String regionId, String world, String regionKind, String sellType,
                              boolean sold, String owner, double price, boolean subregion) {
            this.regionId = regionId;
            this.world = world;
            this.regionKind = regionKind;
            this.sellType = sellType;
            this.sold = sold;
            this.owner = owner;
            this.price = price;
            this.subregion = subregion;
        }
    }

    public static class OwnerStats {
        public final String owner;
        public final int regionCount;
        public final int purchases;
        public final double totalSpent;

        public OwnerStats(String owner, int regionCount, int purchases, double totalSpent) {
            this.owner = owner;
            this.regionCount = regionCount;
            this.purchases = purchases;
            this.totalSpent = totalSpent;
        }
    }

    public final long generatedAtMillis;
    public final int totalRegions;
    public final int soldRegions;
    public final int availableRegions;
    public final int totalSales;
    public final double totalSalesVolume;
    public final List<OwnerStats> topOwners;
    public final List<RegionListing> regions;

    public RegionMarketSnapshot(long generatedAtMillis, int totalRegions, int soldRegions, int availableRegions,
                                 int totalSales, double totalSalesVolume,
                                 List<OwnerStats> topOwners, List<RegionListing> regions) {
        this.generatedAtMillis = generatedAtMillis;
        this.totalRegions = totalRegions;
        this.soldRegions = soldRegions;
        this.availableRegions = availableRegions;
        this.totalSales = totalSales;
        this.totalSalesVolume = totalSalesVolume;
        this.topOwners = topOwners;
        this.regions = regions;
    }

    public static RegionMarketSnapshot empty() {
        return new RegionMarketSnapshot(System.currentTimeMillis(), 0, 0, 0, 0, 0,
                Collections.emptyList(), Collections.emptyList());
    }
}
