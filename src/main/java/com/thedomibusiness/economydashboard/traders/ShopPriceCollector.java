package com.thedomibusiness.economydashboard.traders;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the CURRENT price list straight out of dtlTradersPlus' shop config files
 * (plugins/dtlTradersPlus/shops/&lt;shop&gt;/&lt;shop&gt;.yml) - not the transaction
 * logs. This is a live snapshot of what's configured right now, so it's re-read in
 * full on every {@link #collect()} call rather than tracked incrementally like the
 * log collector.
 *
 * Only "tradable" items (dtlTradersPlus' regular buy/sell entries with a plain
 * trade-price) are covered - "commands" reward-items and barter-only "trade" items
 * don't have a simple money price and are skipped.
 */
public class ShopPriceCollector {

    private static class MutableEntry {
        String shop;
        String page;
        String itemName;
        Double buyPrice;
        Double sellPrice;
    }

    private final File shopsRoot;

    public ShopPriceCollector(Plugin plugin) {
        File pluginsFolder = plugin.getDataFolder().getParentFile();
        this.shopsRoot = new File(pluginsFolder, "dtlTradersPlus/shops");
    }

    public List<ShopPriceEntry> collect() {
        Map<String, MutableEntry> merged = new LinkedHashMap<>();
        File[] shopDirs = shopsRoot.listFiles(File::isDirectory);
        if (shopDirs != null) {
            for (File shopDir : shopDirs) {
                File shopFile = new File(shopDir, shopDir.getName() + ".yml");
                if (!shopFile.isFile()) {
                    continue;
                }
                try {
                    readShop(shopFile, shopDir.getName(), merged);
                } catch (Exception e) {
                    // skip shops we can't parse rather than failing the whole collection
                }
            }
        }

        List<ShopPriceEntry> result = new ArrayList<>();
        for (MutableEntry e : merged.values()) {
            result.add(new ShopPriceEntry(e.shop, e.page, e.itemName, e.buyPrice, e.sellPrice));
        }
        return result;
    }

    private void readShop(File shopFile, String shopFolderName, Map<String, MutableEntry> out) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(shopFile);
        for (String shopKey : yaml.getKeys(false)) {
            ConfigurationSection shopSection = yaml.getConfigurationSection(shopKey);
            if (shopSection == null) {
                continue;
            }
            ConfigurationSection pages = shopSection.getConfigurationSection("pages");
            if (pages == null) {
                continue;
            }
            for (String pageKey : pages.getKeys(false)) {
                ConfigurationSection page = pages.getConfigurationSection(pageKey);
                if (page == null) {
                    continue;
                }
                String pageName = page.getString("page-name", pageKey);
                readItems(page.getConfigurationSection("buy-items"), shopFolderName, pageName, true, out);
                readItems(page.getConfigurationSection("sell-items"), shopFolderName, pageName, false, out);
            }
        }
    }

    private void readItems(ConfigurationSection itemsSection, String shop, String page, boolean isBuySide,
                            Map<String, MutableEntry> out) {
        if (itemsSection == null) {
            return;
        }
        for (String itemKey : itemsSection.getKeys(false)) {
            ConfigurationSection item = itemsSection.getConfigurationSection(itemKey);
            if (item == null || !"tradable".equalsIgnoreCase(item.getString("type"))) {
                continue;
            }
            String name = itemDisplayName(item);
            double price = item.getDouble("trade-price");

            String key = shop + "|" + page + "|" + name;
            MutableEntry entry = out.computeIfAbsent(key, k -> {
                MutableEntry e = new MutableEntry();
                e.shop = shop;
                e.page = page;
                e.itemName = name;
                return e;
            });
            if (isBuySide) {
                entry.buyPrice = price;
            } else {
                entry.sellPrice = price;
            }
        }
    }

    private String itemDisplayName(ConfigurationSection item) {
        String displayName = item.getString("display-name");
        if (displayName != null && !displayName.isEmpty()) {
            return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', displayName));
        }
        try {
            if (item.isItemStack("item")) {
                ItemStack stack = item.getItemStack("item");
                if (stack != null) {
                    return prettifyMaterialName(stack.getType().name());
                }
            } else if (item.isConfigurationSection("item")) {
                String type = item.getConfigurationSection("item").getString("type");
                if (type != null) {
                    return prettifyMaterialName(type);
                }
            }
        } catch (Exception ignored) {
        }
        return "?";
    }

    private String prettifyMaterialName(String materialName) {
        String[] parts = materialName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
