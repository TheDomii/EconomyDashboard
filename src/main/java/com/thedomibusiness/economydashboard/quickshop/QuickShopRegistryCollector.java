package com.thedomibusiness.economydashboard.quickshop;

import com.ghostchu.quickshop.QuickShop;
import com.ghostchu.quickshop.api.QuickShopAPI;
import com.ghostchu.quickshop.api.database.DatabaseHelper;
import com.ghostchu.quickshop.api.database.bean.DataRecord;
import com.ghostchu.quickshop.api.database.bean.InfoRecord;
import com.ghostchu.quickshop.api.database.bean.ShopRecord;
import com.ghostchu.quickshop.api.obj.QUser;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads QuickShop's shop registry straight from its own database (DatabaseHelper#listShops())
 * instead of the live ShopManager#getAllShops() list. The live list only reflects shops whose
 * chunk is currently loaded (ContainerShop#isLoaded()/isValid() both force a chunk load/generate
 * if called on an unloaded one, which stalled the server on real-world data - see git history),
 * so on a server where nobody has walked past most shops yet it returns close to nothing. The
 * database join (DATA + SHOPS + SHOP_MAP tables) has no such requirement: it's a plain SQL read,
 * the same one QuickShop's own /quickshop database commands use. Item names come back as a
 * serialized ItemStack string, decoded via QuickShop.getInstance().platform().decodeStack() -
 * no public API equivalent exists (the public API's own Util#deserialize uses the older
 * YAML-based format and silently fails against modern component-based item data, verified
 * empirically), hence the reach into QuickShop's internal (non-api) classes. Chunk-safe end to
 * end. Runs on an async task (see EconomyDashboardPlugin) since it's a direct DB read, not a
 * Bukkit API call.
 */
public class QuickShopRegistryCollector {

    public List<QuickShopSnapshot.ShopListing> collect() {
        List<QuickShopSnapshot.ShopListing> result = new ArrayList<>();
        DatabaseHelper databaseHelper = QuickShopAPI.getInstance().getDatabaseHelper();
        List<ShopRecord> records = databaseHelper.listShops(false);

        for (ShopRecord record : records) {
            try {
                DataRecord data = record.getDataRecord();
                InfoRecord info = record.getInfoRecord();

                QUser owner = data.getOwner();
                String ownerName = owner != null && owner.getUsername() != null ? owner.getUsername() : "?";
                String itemName = itemName(data.getItem());
                double price = data.getPrice();
                // QuickShop's regular shop type: 0 = selling (shop sells to players), 1 = buying
                // (shop buys from players) - the same convention ContainerShop's own type ID uses.
                boolean shopBuys = data.getType() == 1;
                String location = info.getWorld() + ":" + info.getX() + ":" + info.getY() + ":" + info.getZ();

                result.add(new QuickShopSnapshot.ShopListing(ownerName, itemName, price, shopBuys, location));
            } catch (Exception ignored) {
                // skip records that fail to resolve (e.g. corrupt item data) rather than aborting the whole poll
            }
        }
        return result;
    }

    private String itemName(String serializedItem) {
        if (serializedItem == null || serializedItem.isEmpty()) {
            return "?";
        }
        try {
            ItemStack item = QuickShop.getInstance().platform().decodeStack(serializedItem);
            return item != null ? prettify(item.getType().name()) : "?";
        } catch (Exception e) {
            return "?";
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
