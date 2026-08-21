package com.thedomibusiness.economydashboard.traders;

public class Transaction {

    public enum Type { BUY, SELL, TRADE }

    public final long timestampMillis;
    public final Type type;
    public final String player;
    public final String shop;
    public final String page;
    public final double price;
    public final String itemName;
    public final int itemAmount;

    public Transaction(long timestampMillis, Type type, String player, String shop, String page, double price,
                        String itemName, int itemAmount) {
        this.timestampMillis = timestampMillis;
        this.type = type;
        this.player = player;
        this.shop = shop;
        this.page = page;
        this.price = price;
        this.itemName = itemName;
        this.itemAmount = itemAmount;
    }
}
