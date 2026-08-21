package com.thedomibusiness.economydashboard.traders;

public class ShopPriceEntry {
    public final String shop;
    public final String page;
    public final String itemName;
    public final Double buyPrice;
    public final Double sellPrice;

    public ShopPriceEntry(String shop, String page, String itemName, Double buyPrice, Double sellPrice) {
        this.shop = shop;
        this.page = page;
        this.itemName = itemName;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }
}
