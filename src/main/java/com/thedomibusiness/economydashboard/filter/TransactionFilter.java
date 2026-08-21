package com.thedomibusiness.economydashboard.filter;

import java.util.Map;

/**
 * Shared filter shape for the dtlTradersPlus and QuickShop transaction exports.
 * All fields are optional (null = no restriction on that field).
 */
public class TransactionFilter {

    public Long fromMillis;
    public Long toMillis;
    public String type;
    public String player;
    /** Shop name (dtlTradersPlus) or shop owner (QuickShop) - both are "who ran the shop". */
    public String counterparty;
    public String item;
    public Double minPrice;
    public Double maxPrice;
    public int limit = 5000;

    /**
     * Reads from=..&to=..&type=..&player=..&shop=..(or owner=..)&item=..&minPrice=..&maxPrice=..&limit=..
     * "from"/"to" accept either epoch millis or ISO-8601 "yyyy-MM-dd" (interpreted as UTC midnight).
     */
    public static TransactionFilter fromQuery(Map<String, String> query, String counterpartyParamName) {
        TransactionFilter f = new TransactionFilter();
        f.fromMillis = parseTimestamp(query.get("from"));
        f.toMillis = parseTimestamp(query.get("to"));
        f.type = emptyToNull(query.get("type"));
        f.player = emptyToNull(query.get("player"));
        f.counterparty = emptyToNull(query.get(counterpartyParamName));
        f.item = emptyToNull(query.get("item"));
        f.minPrice = parseDouble(query.get("minPrice"));
        f.maxPrice = parseDouble(query.get("maxPrice"));
        String limitRaw = query.get("limit");
        if (limitRaw != null) {
            try {
                int parsed = Integer.parseInt(limitRaw);
                if (parsed > 0 && parsed <= 50000) {
                    f.limit = parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return f;
    }

    private static Long parseTimestamp(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        raw = raw.trim();
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
        }
        try {
            return java.time.LocalDate.parse(raw)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Double parseDouble(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
