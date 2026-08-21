package com.thedomibusiness.economydashboard.quickshop;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Shop;

import java.util.ArrayList;
import java.util.List;

/**
 * Polls QuickShop's own live shop registry (ShopManager#getAllShops()) - it tracks
 * every shop in a database, so this doesn't need to reconstruct anything from events.
 */
public class QuickShopRegistryCollector {

    @SuppressWarnings("unchecked")
    public List<QuickShopSnapshot.ShopListing> collect() {
        List<QuickShopSnapshot.ShopListing> result = new ArrayList<>();
        List<Shop> shops = QuickShopAPI.getInstance().getShopManager().getAllShops();
        for (Shop shop : shops) {
            try {
                // isValid() calls Location#getBlock() internally, which force-loads (and on
                // an ungenerated world, force-generates) the shop's chunk - on the main thread
                // that can stall the server when real shop data spans an unexplored world.
                // isLoaded() is a plain cached flag QuickShop maintains via chunk load/unload
                // events, so it never touches the chunk system: shops whose chunk isn't
                // currently loaded are simply skipped for this poll instead of forcing a load.
                if (!shop.isLoaded()) {
                    continue;
                }
                QUser owner = shop.getOwner();
                String ownerName = owner != null && owner.getUsername() != null ? owner.getUsername() : "?";
                String itemName = shop.getItem() != null ? prettify(shop.getItem().getType().name()) : "?";
                double price = shop.getPrice();
                boolean shopBuys = shop.shopType() != null && shop.shopType().isBuying();
                String location = locationKey(shop);

                result.add(new QuickShopSnapshot.ShopListing(ownerName, itemName, price, shopBuys, location));
            } catch (Exception ignored) {
                // skip shops that fail to resolve (e.g. mid-unload) rather than aborting the whole poll
            }
        }
        return result;
    }

    private String locationKey(Shop shop) {
        try {
            // Shop#getShopBlock() calls Location#getBlock(), which force-loads (and on an
            // ungenerated world, force-generates) the chunk if it isn't already loaded - on
            // the main thread that can stall the server for real-world shop data spread across
            // an unexplored world. bukkitLocation() returns the shop's cached Location field
            // directly, no chunk access needed.
            org.bukkit.Location loc = shop.bukkitLocation();
            return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        } catch (Exception e) {
            return String.valueOf(System.identityHashCode(shop));
        }
    }

    private String prettify(String materialName) {
        String[] parts = materialName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
