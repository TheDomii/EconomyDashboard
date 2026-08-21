package com.thedomibusiness.economydashboard.traders;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses dtlTradersPlus log lines (plugins/dtlTradersPlus/shops/&lt;shop&gt;/logs/&lt;date&gt;.log).
 * The format is free text written by the plugin itself, reconstructed from its
 * decompiled source (com.degitise.minevid.dtlTraders.utils.Utils#logTradableGUIItem /
 * #logCommandsGUIItem / #logTradeItem), not a stable documented format.
 *
 * Example lines:
 *   [20/08/2026 21:55:00][BUY]Steve bought 5x Diamond for 12.50 in shop "Waffenladen" on page "Hauptseite"
 *   [20/08/2026 21:55:00][SELL]Steve sold 3x Iron Ingot for 4.50 in shop "Waffenladen" on page "Hauptseite"
 *   [20/08/2026 21:55:00][TRADE]Steve traded 2x Emerald for 1x Diamond in shop Tauschhandel on page "Seite1" with an extra price: 5.0
 *
 * Note: TRADE lines don't quote the shop name (a quirk of dtlTradersPlus itself), so a
 * trade shop whose name contains a space cannot be parsed reliably from that line alone.
 */
public class DtlLogParser {

    private static final Pattern BUY_SELL = Pattern.compile(
            "^\\[(\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2})]\\[(BUY|SELL)](.+?) (?:bought|sold) (\\d+)x (.+?) for ([0-9.,]+).*? in shop \"(.+?)\" on page \"(.+?)\"$");

    private static final Pattern TRADE = Pattern.compile(
            "^\\[(\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2})]\\[TRADE](.+?) traded (.+?) for (.+?) in shop (\\S+) on page \"(.+?)\"(?: with an extra price: ([0-9.]+))?$");

    public Optional<Transaction> parse(String line) {
        if (line == null || line.isEmpty()) {
            return Optional.empty();
        }

        Matcher buySell = BUY_SELL.matcher(line);
        if (buySell.matches()) {
            Transaction.Type type = "BUY".equals(buySell.group(2)) ? Transaction.Type.BUY : Transaction.Type.SELL;
            String player = buySell.group(3);
            int amount = parseInt(buySell.group(4), 1);
            String item = buySell.group(5);
            double price = parsePrice(buySell.group(6));
            String shop = buySell.group(7);
            String page = buySell.group(8);
            return Optional.of(new Transaction(type, player, shop, page, price, item, amount));
        }

        Matcher trade = TRADE.matcher(line);
        if (trade.matches()) {
            String player = trade.group(2);
            String shop = trade.group(5);
            String page = trade.group(6);
            double price = trade.group(7) != null ? parsePrice(trade.group(7)) : 0.0;
            return Optional.of(new Transaction(Transaction.Type.TRADE, player, shop, page, price, null, 0));
        }

        return Optional.empty();
    }

    private double parsePrice(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
