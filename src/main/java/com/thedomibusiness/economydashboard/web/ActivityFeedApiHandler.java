package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.activity.ActivityEvent;
import com.thedomibusiness.economydashboard.activity.ActivityFeedService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ActivityFeedApiHandler implements HttpHandler {

    private static final int DEFAULT_LIMIT = 30;
    private static final int MAX_LIMIT = 100;

    private final ActivityFeedService activityFeedService;

    public ActivityFeedApiHandler(ActivityFeedService activityFeedService) {
        this.activityFeedService = activityFeedService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        int limit = DEFAULT_LIMIT;
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && "limit".equals(pair.substring(0, eq))) {
                    try {
                        limit = Math.min(MAX_LIMIT, Math.max(1, Integer.parseInt(pair.substring(eq + 1))));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        List<ActivityEvent> events = activityFeedService.recent(limit);

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < events.size(); i++) {
            ActivityEvent e = events.get(i);
            if (i > 0) json.append(",");
            json.append("{\"timestamp\":").append(e.timestampMillis).append(",")
                    .append("\"source\":").append(JsonUtil.quoteOrNull(e.source)).append(",")
                    .append("\"player\":").append(JsonUtil.quoteOrNull(e.player)).append(",")
                    .append("\"description\":").append(JsonUtil.quoteOrNull(e.description))
                    .append("}");
        }
        json.append("]");

        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }
}
