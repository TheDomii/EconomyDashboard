package com.thedomibusiness.economydashboard.notify;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Optional Discord alert for HIGH-severity anomalies (config.yml -> webhook.discord-url, empty
 * by default = disabled). Fire-and-forget: runs off-thread already (called from the async
 * anomaly-detection task), and any failure (bad URL, network error, Discord outage) is only
 * logged, never allowed to break anomaly detection itself. Hand-rolled JSON body, same
 * no-dependency approach as the rest of the plugin's JSON handling.
 */
public class DiscordWebhookNotifier {

    private final Plugin plugin;
    private final String webhookUrl;

    public DiscordWebhookNotifier(Plugin plugin, String webhookUrl) {
        this.plugin = plugin;
        this.webhookUrl = webhookUrl;
    }

    public boolean isEnabled() {
        return webhookUrl != null && !webhookUrl.trim().isEmpty();
    }

    public void sendHighSeverityAlert(String title, String description, String linkUrl, String dashboardBaseUrl) {
        if (!isEnabled()) {
            return;
        }
        String content = "🔴 **" + title + "**\n" + description
                + (linkUrl != null && dashboardBaseUrl != null ? "\n" + dashboardBaseUrl + linkUrl : "");
        String json = "{\"content\":" + quote(content) + "}";

        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(webhookUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
            }
            int status = conn.getResponseCode();
            if (status >= 300) {
                plugin.getLogger().warning("Discord-Webhook antwortete mit Status " + status);
            }
            conn.disconnect();
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Discord-Webhook fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
