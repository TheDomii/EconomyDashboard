package com.thedomibusiness.economydashboard.auth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    public static final String COOKIE_NAME = "ecodash_session";

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final long sessionDurationMillis;

    public SessionManager(long sessionMinutes) {
        this.sessionDurationMillis = sessionMinutes * 60_000L;
    }

    public String createSession() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, System.currentTimeMillis() + sessionDurationMillis);
        return token;
    }

    public boolean isValid(String token) {
        if (token == null) {
            return false;
        }
        Long expiry = sessions.get(token);
        if (expiry == null) {
            return false;
        }
        if (expiry < System.currentTimeMillis()) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }
}
