package com.thedomibusiness.economydashboard.quickshop;

import com.ghostchu.quickshop.api.event.economy.ShopSuccessPurchaseEvent;
import com.ghostchu.quickshop.api.obj.QUser;
import com.ghostchu.quickshop.api.shop.Shop;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.logging.Level;

public class QuickShopListener implements Listener {

    private final Plugin plugin;
    private final QuickShopDatabase db;

    public QuickShopListener(Plugin plugin, QuickShopDatabase db) {
        this.plugin = plugin;
        this.db = db;
    }

    @SuppressWarnings("unchecked")
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPurchase(ShopSuccessPurchaseEvent event) {
        try {
            Shop shop = event.getShop();
            QUser purchaser = event.getPurchaser();

            String player = purchaser != null && purchaser.getUsername() != null ? purchaser.getUsername() : "?";
            QUser owner = shop != null ? shop.getOwner() : null;
            String ownerName = owner != null && owner.getUsername() != null ? owner.getUsername() : null;
            String itemName = shop != null && shop.getItem() != null ? prettify(shop.getItem().getType().name()) : "?";
            boolean shopBuys = shop != null && shop.shopType() != null && shop.shopType().isBuying();

            // A "buying" shop pays the player out (a payout for the shop owner); a
            // "selling" shop is paid by the player (revenue for the shop owner).
            String type = shopBuys ? "SELL" : "BUY";

            db.insertTransaction(type, player, ownerName, itemName, event.getAmount(), event.getBalance());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte QuickShop-Transaktion nicht speichern", e);
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
