package com.volcanoEvent.managers;

import com.volcanoEvent.VolcanoEventPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final VolcanoEventPlugin plugin;

    public PlayerJoinListener(VolcanoEventPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getEventManager().onPlayerJoin(event.getPlayer());
    }
}
