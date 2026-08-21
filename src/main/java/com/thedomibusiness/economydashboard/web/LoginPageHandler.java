package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.thedomibusiness.economydashboard.auth.LoginConfig;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class LoginPageHandler implements HttpHandler {

    private final Plugin plugin;
    private final LoginConfig config;

    public LoginPageHandler(Plugin plugin, LoginConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String template;
        try (InputStream in = plugin.getResource("web/login.html")) {
            if (in == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            template = buffer.toString("UTF-8");
        }

        String backgroundCss;
        if (!config.hasBackground()) {
            backgroundCss = "";
        } else if (config.isBackgroundUrl()) {
            backgroundCss = "background-image: url('" + escapeCssUrl(config.backgroundImage) + "');";
        } else {
            backgroundCss = "background-image: url('/login-background');";
        }

        String query = exchange.getRequestURI().getRawQuery();
        String errorHtml = (query != null && query.contains("error=1"))
                ? "<div class=\"error\">Benutzername oder Passwort falsch.</div>"
                : "";

        String page = template.replace("__BACKGROUND_CSS__", backgroundCss).replace("__ERROR_HTML__", errorHtml);
        byte[] body = page.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
        }
    }

    private String escapeCssUrl(String url) {
        return url.replace("'", "%27").replace("\"", "%22");
    }
}
