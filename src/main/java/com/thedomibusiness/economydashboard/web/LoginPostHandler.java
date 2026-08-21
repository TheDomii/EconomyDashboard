package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.auth.LoginConfig;
import com.thedomibusiness.economydashboard.auth.SessionManager;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class LoginPostHandler implements HttpHandler {

    private final Plugin plugin;
    private final LoginConfig config;
    private final SessionManager sessionManager;

    public LoginPostHandler(Plugin plugin, LoginConfig config, SessionManager sessionManager) {
        this.plugin = plugin;
        this.config = config;
        this.sessionManager = sessionManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String body;
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            body = buffer.toString("UTF-8");
        }

        Map<String, String> form = parseForm(body);
        String username = form.getOrDefault("username", "");
        String password = form.getOrDefault("password", "");
        String remote = exchange.getRemoteAddress() != null ? exchange.getRemoteAddress().toString() : "unbekannt";

        if (username.equals(config.username) && password.equals(config.password)) {
            String token = sessionManager.createSession();
            plugin.getLogger().info("Dashboard-Login erfolgreich: " + username + " von " + remote);

            exchange.getResponseHeaders().add("Set-Cookie",
                    SessionManager.COOKIE_NAME + "=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=" + (config.sessionMinutes * 60));
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(302, -1);
        } else {
            plugin.getLogger().log(Level.WARNING, "Fehlgeschlagener Dashboard-Login: Benutzername '" + username + "' von " + remote);
            exchange.getResponseHeaders().set("Location", "/login?error=1");
            exchange.sendResponseHeaders(302, -1);
        }
    }

    private Map<String, String> parseForm(String body) {
        Map<String, String> result = new HashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            try {
                result.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
            } catch (Exception e) {
                result.put(key, value);
            }
        }
        return result;
    }
}
