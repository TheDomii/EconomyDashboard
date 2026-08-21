package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.auth.SessionManager;

import java.io.IOException;
import java.util.List;

public class LogoutHandler implements HttpHandler {

    private final SessionManager sessionManager;

    public LogoutHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders != null) {
            for (String header : cookieHeaders) {
                for (String part : header.split(";")) {
                    String[] kv = part.trim().split("=", 2);
                    if (kv.length == 2 && kv[0].equals(SessionManager.COOKIE_NAME)) {
                        sessionManager.invalidate(kv[1]);
                    }
                }
            }
        }

        exchange.getResponseHeaders().add("Set-Cookie", SessionManager.COOKIE_NAME + "=; Path=/; Max-Age=0");
        exchange.getResponseHeaders().set("Location", "/login");
        exchange.sendResponseHeaders(302, -1);
    }
}
