package com.thedomibusiness.economydashboard.quickshop;

import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Shop;

import java.util.ArrayList;
import java.util.List;

/**
 * Polls QuickShop's own live shop registry (ShopManager#getAllShops()) - QuickShop,
 * unlike ChestShop, actually tracks every shop in a database, so this doesn't need
 * to reconstruct anything from events.
 */
public class QuickShopRegistryCollector {

    @SuppressWarnings("unchecked")
    public List<QuickShopSnapshot.ShopListing> collect() {
        List<QuickShopSnapshot.ShopListing> result = new ArrayList<>();
        List<Shop> shops = QuickShopAPI.getInstance().getShopManager().getAllShops();
        for (Shop shop : shops) {
            try {
                if (!shop.isValid()) {
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
            org.bukkit.block.Block block = shop.getShopBlock();
            return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
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
