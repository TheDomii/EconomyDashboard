package com.thedomibusiness.economydashboard.anomaly;

/** One (item, player) pair's total sold quantity - the raw material anomaly detection works from. */
public class SellVolumeRow {
    public final String itemName;
    public final String player;
    public final long quantity;
    /** Which module this came from ("dtlTradersPlus" or "QuickShop") - used to build the drill-down link. */
    public final String source;

    public SellVolumeRow(String itemName, String player, long quantity, String source) {
        this.itemName = itemName;
        this.player = player;
        this.quantity = quantity;
        this.source = source;
    }
}
