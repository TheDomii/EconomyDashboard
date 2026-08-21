package com.thedomibusiness.economydashboard.chestshop;

import com.Acrobot.ChestShop.Events.ShopCreatedEvent;
import com.Acrobot.ChestShop.Events.ShopDestroyedEvent;
import com.Acrobot.ChestShop.Events.ShopEditedEvent;
import com.Acrobot.ChestShop.Events.TransactionEvent;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.logging.Level;

public class ChestShopListener implements Listener {

    private final Plugin plugin;
    private final ChestShopDatabase db;
    private final boolean debug;

    public ChestShopListener(Plugin plugin, ChestShopDatabase db, boolean debug) {
        this.plugin = plugin;
        this.db = db;
        this.debug = debug;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopCreated(ShopCreatedEvent event) {
        try {
            ChestShopSignParser.ParsedSign parsed = ChestShopSignParser.parse(event.getSignLines());
            db.upsertShop(locationKey(event.getSign()), parsed.owner, parsed.item, parsed.quantityRaw,
                    parsed.buyPrice, parsed.sellPrice);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte neuen ChestShop nicht speichern", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShopEdited(ShopEditedEvent event) {
        try {
            ChestShopSignParser.ParsedSign parsed = ChestShopSignParser.parse(event.getNewLines());
            db.upsertShop(locationKey(event.getSign()), parsed.owner, parsed.item, parsed.quantityRaw,
                    parsed.buyPrice, parsed.sellPrice);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte bearbeiteten ChestShop nicht speichern", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onShopDestroyed(ShopDestroyedEvent event) {
        try {
            db.removeShop(locationKey(event.getSign()));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte entfernten ChestShop nicht loeschen", e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransaction(TransactionEvent event) {
        try {
            String owner = ownerFromAccount(event);
            String itemName = itemName(event.getStock());
            int amount = totalAmount(event.getStock());
            double price = event.getExactPrice().doubleValue();
            String type = event.getTransactionType().name();

            db.insertTransaction(type, event.getClient().getName(), owner, itemName, amount, price);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Konnte ChestShop-Transaktion nicht speichern", e);
        } catch (Exception e) {
            if (debug) {
                plugin.getLogger().log(Level.WARNING, "Unerwarteter Fehler beim Verarbeiten einer ChestShop-Transaktion", e);
            }
        }
    }

    private String locationKey(Sign sign) {
        return sign.getWorld().getName() + ":" + sign.getX() + ":" + sign.getY() + ":" + sign.getZ();
    }

    @SuppressWarnings("deprecation")
    private String ownerFromAccount(TransactionEvent event) {
        try {
            OfflinePlayer owner = event.getOwner();
            if (owner != null && owner.getName() != null) {
                return owner.getName();
            }
        } catch (Exception ignored) {
            // group/virtual accounts don't resolve to a player - fall back to the sign
        }
        return ownerFromSign(event.getSign());
    }

    private String ownerFromSign(Sign sign) {
        try {
            String line = sign.getLine(0);
            return line == null || line.isEmpty() ? null : line.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String itemName(ItemStack[] stock) {
        if (stock == null || stock.length == 0 || stock[0] == null) {
            return "?";
        }
        return prettify(stock[0].getType().name());
    }

    private int totalAmount(ItemStack[] stock) {
        if (stock == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack is : stock) {
            if (is != null) {
                total += is.getAmount();
            }
        }
        return total;
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
