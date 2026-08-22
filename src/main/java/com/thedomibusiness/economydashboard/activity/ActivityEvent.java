package com.thedomibusiness.economydashboard.activity;

/** One human-readable line for the "Live-Aktivität" feed, merged from all modules' own transaction tables. */
public class ActivityEvent {

    public final long timestampMillis;
    public final String source;
    public final String player;
    public final String description;

    public ActivityEvent(long timestampMillis, String source, String player, String description) {
        this.timestampMillis = timestampMillis;
        this.source = source;
        this.player = player;
        this.description = description;
    }
}
