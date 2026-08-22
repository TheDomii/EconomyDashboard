package com.thedomibusiness.economydashboard.towny;

import com.thedomibusiness.economydashboard.activity.ActivityEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Feeds Towny lifecycle events (new/deleted towns, joins/leaves, new nations) into the
 * "Live-Aktivität" panel, the same way trader/QuickShop/RegionMarket transactions do. Unlike
 * those modules, Towny has no natural transaction table to read from, so this is a simple
 * in-memory ring buffer fed by Bukkit events (TownyActivityListener) - recent-only, doesn't
 * need to survive a restart the way real transaction history does.
 */
public class TownyActivityService {

    private static final int MAX_EVENTS = 200;

    private final ConcurrentLinkedDeque<ActivityEvent> recent = new ConcurrentLinkedDeque<>();

    public TownyActivityService(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new TownyActivityListener(this), plugin);
    }

    public void record(String description) {
        recent.addFirst(new ActivityEvent(System.currentTimeMillis(), "Towny", null, description));
        while (recent.size() > MAX_EVENTS) {
            recent.removeLast();
        }
    }

    public List<ActivityEvent> recentActivity(int limit) {
        List<ActivityEvent> result = new ArrayList<>();
        Iterator<ActivityEvent> it = recent.iterator();
        while (it.hasNext() && result.size() < limit) {
            result.add(it.next());
        }
        return result;
    }
}
