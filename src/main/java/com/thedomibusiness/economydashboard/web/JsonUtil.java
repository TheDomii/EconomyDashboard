package com.thedomibusiness.economydashboard.web;

import java.util.Locale;

/** Small shared helpers for the hand-rolled JSON building used across the API handlers. */
public class JsonUtil {

    private JsonUtil() {
    }

    public static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String quoteOrNull(String value) {
        return value != null ? "\"" + escape(value) + "\"" : "null";
    }

    public static String money(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
