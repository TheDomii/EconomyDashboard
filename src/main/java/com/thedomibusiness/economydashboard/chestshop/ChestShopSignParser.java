package com.thedomibusiness.economydashboard.chestshop;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort reader for a ChestShop sign's 4 lines. The exact text is whatever
 * the server admin's currency-format settings produce (symbols, separators), so
 * this doesn't try to be a full parser - it just pulls the numbers out in the
 * classic "buy:sell" line order used by ChestShop for as long as it's existed.
 * If a server heavily customizes that format, prices may come back as null.
 */
public class ChestShopSignParser {

    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:[.,][0-9]+)?");

    public static class ParsedSign {
        public final String owner;
        public final String quantityRaw;
        public final Double buyPrice;
        public final Double sellPrice;
        public final String item;

        public ParsedSign(String owner, String quantityRaw, Double buyPrice, Double sellPrice, String item) {
            this.owner = owner;
            this.quantityRaw = quantityRaw;
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
            this.item = item;
        }
    }

    public static ParsedSign parse(String[] lines) {
        if (lines == null || lines.length < 4) {
            return new ParsedSign(null, null, null, null, null);
        }
        String owner = strip(lines[0]);
        String quantity = strip(lines[1]);
        String priceLine = lines[2] == null ? "" : lines[2];
        String item = strip(lines[3]);

        Double buy = null;
        Double sell = null;
        String[] halves = priceLine.split(":", 2);
        if (halves.length == 2) {
            buy = firstNumber(halves[0]);
            sell = firstNumber(halves[1]);
        } else {
            // no ':' - single price, direction (buy vs sell only) isn't recoverable
            // from the sign text alone, so it's reported as a buy price.
            buy = firstNumber(priceLine);
        }

        return new ParsedSign(owner, quantity, buy, sell, item);
    }

    private static Double firstNumber(String text) {
        Matcher m = NUMBER.matcher(text);
        if (!m.find()) {
            return null;
        }
        try {
            return Double.parseDouble(m.group().replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String strip(String s) {
        return s == null ? null : s.trim();
    }
}
