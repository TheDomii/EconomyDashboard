package com.thedomibusiness.economydashboard.auth;

public class LoginConfig {
    public final boolean enabled;
    public final String username;
    public final String password;
    /** Either a full http(s):// URL, or a filename inside the plugin's data folder, or empty. */
    public final String backgroundImage;
    public final long sessionMinutes;

    public LoginConfig(boolean enabled, String username, String password, String backgroundImage, long sessionMinutes) {
        this.enabled = enabled;
        this.username = username;
        this.password = password;
        this.backgroundImage = backgroundImage;
        this.sessionMinutes = sessionMinutes;
    }

    public boolean isBackgroundUrl() {
        return backgroundImage != null && (backgroundImage.startsWith("http://") || backgroundImage.startsWith("https://"));
    }

    public boolean hasBackground() {
        return backgroundImage != null && !backgroundImage.trim().isEmpty();
    }
}
