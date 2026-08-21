package com.thedomibusiness.economydashboard.regionmarket;

import net.alex9849.arm.AdvancedRegionMarket;
import net.alex9849.arm.regions.Region;
import net.alex9849.arm.regions.RegionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Polls AdvancedRegionMarket's own RegionManager (an Iterable<Region> over the
 * top-level regions; subregions hang off Region#getSubregions()). Unlike QuickShop,
 * ARM has no reliable post-purchase event (PreBuyEvent fires and can be cancelled
 * before the actual money/ownership transfer happens), so this only builds the
 * current registry snapshot - sale detection is done by diffing against the
 * previous poll in RegionMarketDatabase#replaceRegions.
 */
public class RegionMarketCollector {

    public List<RegionMarketSnapshot.RegionListing> collect() {
        List<RegionMarketSnapshot.RegionListing> result = new ArrayList<>();
        RegionManager regionManager = AdvancedRegionMarket.getInstance().getRegionManager();
        if (regionManager == null) {
            return result;
        }
        for (Region region : regionManager) {
            addRegion(result, region);
            for (Region subregion : region.getSubregions()) {
                addRegion(result, subregion);
            }
        }
        return result;
    }

    private void addRegion(List<RegionMarketSnapshot.RegionListing> result, Region region) {
        try {
            String regionId = region.getRegion() != null ? region.getRegion().getId() : "?";
            String world = region.getRegionworld() != null ? region.getRegionworld().getName() : "?";
            String regionKind = region.getRegionKind() != null ? region.getRegionKind().getName() : "?";
            String sellType = region.getSellType() != null ? region.getSellType().getInternalName() : "?";
            boolean sold = region.isSold();
            String owner = sold ? region.getOwnerName() : null;
            double price = calcPrice(region);

            result.add(new RegionMarketSnapshot.RegionListing(regionId, world, regionKind, sellType,
                    sold, owner, price, region.isSubregion()));
        } catch (Exception ignored) {
            // skip regions that fail to resolve (e.g. mid-reset) rather than aborting the whole poll
        }
    }

    private double calcPrice(Region region) {
        try {
            if (region.getPriceObject() == null || region.getRegion() == null) {
                return 0;
            }
            return region.getPriceObject().calcPrice(region.getRegion());
        } catch (Exception e) {
            return 0;
        }
    }
}
