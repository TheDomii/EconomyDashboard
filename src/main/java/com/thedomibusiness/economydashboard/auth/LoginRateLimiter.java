package com.thedomibusiness.economydashboard.auth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory brute-force guard for the login form: after MAX_ATTEMPTS failed logins from
 * the same remote IP within WINDOW_MILLIS, further attempts from that IP are blocked for
 * LOCKOUT_MILLIS. Deliberately basic (no persistence, no distributed state) - this dashboard is
 * meant for a local network/VPN, not the open internet (see README's HTTPS caveat); the goal is
 * raising the cost of casual password guessing, not defeating a determined distributed attacker.
 */
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MILLIS = 5L * 60 * 1000;
    private static final long LOCKOUT_MILLIS = 10L * 60 * 1000;

    private static class Entry {
        int attempts;
        long windowStart;
        long lockedUntil;
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public boolean isLocked(String ip) {
        Entry e = entries.get(ip);
        return e != null && System.currentTimeMillis() < e.lockedUntil;
    }

    public long lockedRemainingSeconds(String ip) {
        Entry e = entries.get(ip);
        if (e == null) {
            return 0;
        }
        return Math.max(0, (e.lockedUntil - System.currentTimeMillis()) / 1000);
    }

    public void recordFailure(String ip) {
        long now = System.currentTimeMillis();
        Entry e = entries.computeIfAbsent(ip, k -> new Entry());
        synchronized (e) {
            if (now - e.windowStart > WINDOW_MILLIS) {
                e.windowStart = now;
                e.attempts = 0;
            }
            e.attempts++;
            if (e.attempts >= MAX_ATTEMPTS) {
                e.lockedUntil = now + LOCKOUT_MILLIS;
            }
        }
    }

    public void recordSuccess(String ip) {
        entries.remove(ip);
    }
}
