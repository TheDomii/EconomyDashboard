package com.thedomibusiness.economydashboard.anomaly;

public class Anomaly {

    public enum Severity { HIGH, MEDIUM }

    public final Severity severity;
    public final String category;
    public final String title;
    public final String description;
    public final String linkUrl;
    /** Stable identity across detection cycles (category+title don't change even if the
     *  computed percentage in the description drifts slightly) - used to remember approvals. */
    public final String key;

    public Anomaly(Severity severity, String category, String title, String description, String linkUrl) {
        this.severity = severity;
        this.category = category;
        this.title = title;
        this.description = description;
        this.linkUrl = linkUrl;
        this.key = category + "|" + title;
    }
}
