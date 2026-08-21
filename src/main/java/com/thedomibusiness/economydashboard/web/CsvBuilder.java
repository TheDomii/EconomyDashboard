package com.thedomibusiness.economydashboard.web;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Minimal RFC 4180-ish CSV writer: quotes a field only when it contains a comma,
 * quote or newline (doubling embedded quotes), which is enough for the plain
 * text/numbers this plugin deals with.
 */
public class CsvBuilder {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final StringBuilder sb = new StringBuilder();

    public CsvBuilder header(String... columns) {
        row((Object[]) columns);
        return this;
    }

    public CsvBuilder row(Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(values[i]));
        }
        sb.append("\r\n");
        return this;
    }

    public static String formatTimestamp(long epochMillis) {
        return TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String formatMoney(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String escape(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public String build() {
        return sb.toString();
    }
}
