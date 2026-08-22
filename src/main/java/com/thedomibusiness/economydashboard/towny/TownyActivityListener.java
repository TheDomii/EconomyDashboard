package com.thedomibusiness.economydashboard.towny;

import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.event.NewNationEvent;
import com.palmergames.bukkit.towny.event.NewTownEvent;
import com.palmergames.bukkit.towny.event.TownAddResidentEvent;
import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent;
import com.palmergames.bukkit.towny.event.resident.NewResidentEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class TownyActivityListener implements Listener {

    private final TownyActivityService service;

    public TownyActivityListener(TownyActivityService service) {
        this.service = service;
    }

    @EventHandler
    public void onNewTown(NewTownEvent event) {
        service.record("Stadt \"" + event.getTown().getName() + "\" wurde gegruendet");
    }

    @EventHandler
    public void onDeleteTown(DeleteTownEvent event) {
        service.record("Stadt \"" + event.getTownName() + "\" wurde aufgeloest");
    }

    @EventHandler
    public void onTownAddResident(TownAddResidentEvent event) {
        service.record(event.getResident().getName() + " ist der Stadt \"" + event.getTown().getName() + "\" beigetreten");
    }

    @EventHandler
    public void onTownRemoveResident(TownRemoveResidentEvent event) {
        service.record(event.getResident().getName() + " hat die Stadt \"" + event.getTown().getName() + "\" verlassen");
    }

    @EventHandler
    public void onNewResident(NewResidentEvent event) {
        service.record(event.getResident().getName() + " wurde als neuer Spieler bei Towny registriert");
    }

    @EventHandler
    public void onNewNation(NewNationEvent event) {
        service.record("Nation \"" + event.getNation().getName() + "\" wurde gegruendet");
    }
}
