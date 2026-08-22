package com.thedomibusiness.economydashboard.presence;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerPresenceListener implements Listener {

    private final PlayerPresenceService service;

    public PlayerPresenceListener(PlayerPresenceService service) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.recordJoin(event.getPlayer().getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.recordQuit(event.getPlayer().getName());
    }
}
