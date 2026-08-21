package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.regionmarket.RegionMarketSnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Supplier;

public class RegionMarketApiHandler implements HttpHandler {

    private final Supplier<RegionMarketSnapshot> snapshotSupplier;

    public RegionMarketApiHandler(Supplier<RegionMarketSnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        RegionMarketSnapshot snapshot = snapshotSupplier.get();
        byte[] body = toJson(snapshot).getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String toJson(RegionMarketSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"generatedAtMillis\":").append(snapshot.generatedAtMillis).append(",");
        sb.append("\"totalRegions\":").append(snapshot.totalRegions).append(",");
        sb.append("\"soldRegions\":").append(snapshot.soldRegions).append(",");
        sb.append("\"availableRegions\":").append(snapshot.availableRegions).append(",");
        sb.append("\"totalSales\":").append(snapshot.totalSales).append(",");
        sb.append("\"totalSalesVolume\":").append(format(snapshot.totalSalesVolume)).append(",");

        sb.append("\"topOwners\":[");
        for (int i = 0; i < snapshot.topOwners.size(); i++) {
            RegionMarketSnapshot.OwnerStats o = snapshot.topOwners.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"owner\":\"").append(escape(o.owner)).append("\",")
              .append("\"regionCount\":").append(o.regionCount).append(",")
              .append("\"purchases\":").append(o.purchases).append(",")
              .append("\"totalSpent\":").append(format(o.totalSpent)).append("}");
        }
        sb.append("],");

        sb.append("\"regions\":[");
        for (int i = 0; i < snapshot.regions.size(); i++) {
            RegionMarketSnapshot.RegionListing r = snapshot.regions.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"regionId\":\"").append(escape(nullToEmpty(r.regionId))).append("\",")
              .append("\"world\":\"").append(escape(nullToEmpty(r.world))).append("\",")
              .append("\"regionKind\":\"").append(escape(nullToEmpty(r.regionKind))).append("\",")
              .append("\"sellType\":\"").append(escape(nullToEmpty(r.sellType))).append("\",")
              .append("\"sold\":").append(r.sold).append(",")
              .append("\"owner\":\"").append(escape(nullToEmpty(r.owner))).append("\",")
              .append("\"price\":").append(format(r.price)).append(",")
              .append("\"subregion\":").append(r.subregion).append("}");
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
