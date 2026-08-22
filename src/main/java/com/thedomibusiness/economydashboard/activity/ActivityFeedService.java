package com.thedomibusiness.economydashboard.activity;

import com.thedomibusiness.economydashboard.quickshop.QuickShopService;
import com.thedomibusiness.economydashboard.regionmarket.RegionMarketService;
import com.thedomibusiness.economydashboard.towny.TownyActivityService;
import com.thedomibusiness.economydashboard.traders.DtlTradersLogCollector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Merges the most recent transactions across all active modules into one newest-first feed,
 * so the overview page can show "what just happened" without visiting each module's own page.
 */
public class ActivityFeedService {

    private final DtlTradersLogCollector tradersCollector;
    private final QuickShopService quickShopService;
    private final RegionMarketService regionMarketService;
    private final TownyActivityService townyActivityService;

    public ActivityFeedService(DtlTradersLogCollector tradersCollector, QuickShopService quickShopService,
                                RegionMarketService regionMarketService, TownyActivityService townyActivityService) {
        this.tradersCollector = tradersCollector;
        this.quickShopService = quickShopService;
        this.regionMarketService = regionMarketService;
        this.townyActivityService = townyActivityService;
    }

    public List<ActivityEvent> recent(int limit) {
        List<ActivityEvent> combined = new ArrayList<>();
        if (tradersCollector != null) {
            combined.addAll(tradersCollector.recentActivity(limit));
        }
        if (quickShopService != null) {
            combined.addAll(quickShopService.recentActivity(limit));
        }
        if (regionMarketService != null) {
            combined.addAll(regionMarketService.recentActivity(limit));
        }
        if (townyActivityService != null) {
            combined.addAll(townyActivityService.recentActivity(limit));
        }
        combined.sort(Comparator.comparingLong((ActivityEvent e) -> e.timestampMillis).reversed());
        return combined.size() > limit ? combined.subList(0, limit) : combined;
    }
}
