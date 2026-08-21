package com.thedomibusiness.economydashboard.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class LoginBackgroundHandler implements HttpHandler {

    private final Plugin plugin;
    private final String fileName;

    public LoginBackgroundHandler(Plugin plugin, String fileName) {
        this.plugin = plugin;
        this.fileName = fileName;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.isFile()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", guessContentType(fileName));
        exchange.sendResponseHeaders(200, file.length());
        try (OutputStream os = exchange.getResponseBody(); FileInputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
        }
    }

    private String guessContentType(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "image/jpeg";
    }
}
