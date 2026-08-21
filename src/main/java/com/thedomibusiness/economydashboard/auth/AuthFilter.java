package com.thedomibusiness.economydashboard.auth;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Gatekeeper for every request except the login page/assets. Browser navigation
 * gets redirected to /login; API calls (path starts with /api/) get a plain 401
 * instead, so fetch() calls in the dashboard fail predictably rather than
 * receiving an HTML login page as if it were JSON.
 */
public class AuthFilter extends Filter {

    private final SessionManager sessionManager;

    public AuthFilter(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public String description() {
        return "EconomyDashboard session auth";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (isPublicPath(path)) {
            chain.doFilter(exchange);
            return;
        }

        String token = readCookie(exchange, SessionManager.COOKIE_NAME);
        if (sessionManager.isValid(token)) {
            chain.doFilter(exchange);
            return;
        }

        if (path.startsWith("/api/")) {
            byte[] body = "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(401, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        } else {
            exchange.getResponseHeaders().set("Location", "/login");
            exchange.sendResponseHeaders(302, -1);
        }
    }

    private boolean isPublicPath(String path) {
        return path.equals("/login") || path.equals("/login-background");
    }

    private String readCookie(HttpExchange exchange, String name) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) {
            return null;
        }
        for (String header : cookieHeaders) {
            for (String part : header.split(";")) {
                String[] kv = part.trim().split("=", 2);
                if (kv.length == 2 && kv[0].equals(name)) {
                    return kv[1];
                }
            }
        }
        return null;
    }
}
