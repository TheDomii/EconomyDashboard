package com.thedomibusiness.economydashboard.anomaly;

import java.util.Collections;
import java.util.List;

public class AnomalyReport {
    public final long generatedAtMillis;
    public final List<Anomaly> anomalies;

    public AnomalyReport(long generatedAtMillis, List<Anomaly> anomalies) {
        this.generatedAtMillis = generatedAtMillis;
        this.anomalies = anomalies;
    }

    public static AnomalyReport empty() {
        return new AnomalyReport(System.currentTimeMillis(), Collections.emptyList());
    }
}
