package com.thedomibusiness.economydashboard.towny;

public class TownyResidentSummary {
    public final String townName;
    public final String nationName;
    public final long joinedTownAtMillis;
    public final long registeredMillis;
    public final int ownedPlots;

    public TownyResidentSummary(String townName, String nationName, long joinedTownAtMillis,
                                 long registeredMillis, int ownedPlots) {
        this.townName = townName;
        this.nationName = nationName;
        this.joinedTownAtMillis = joinedTownAtMillis;
        this.registeredMillis = registeredMillis;
        this.ownedPlots = ownedPlots;
    }
}
