package com.thedomibusiness.economydashboard.anomaly;

/** One anomaly a human has reviewed and marked as "checked, this is fine" - kept for the archive. */
public class ArchivedAnomaly {

    public final String key;
    public final String severity;
    public final String category;
    public final String title;
    public final String description;
    public final String linkUrl;
    public final long approvedAtMillis;

    public ArchivedAnomaly(String key, String severity, String category, String title, String description,
                            String linkUrl, long approvedAtMillis) {
        this.key = key;
        this.severity = severity;
        this.category = category;
        this.title = title;
        this.description = description;
        this.linkUrl = linkUrl;
        this.approvedAtMillis = approvedAtMillis;
    }
}
