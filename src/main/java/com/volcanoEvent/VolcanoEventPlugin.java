package com.volcanoEvent;

import com.volcanoEvent.managers.EventManager;
import com.volcanoEvent.managers.PlayerJoinListener;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class VolcanoEventPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    private EventManager eventManager;
    private static final List<String> SUBCOMMANDS = Arrays.asList("start", "stop", "skip", "pause", "setpoint");

    @Override
    public void onEnable() {
        eventManager = new EventManager(this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getCommand("volcano").setExecutor(this);
        getCommand("volcano").setTabCompleter(this);
        getLogger().info("VolcanoEvent v2 enabled.");
    }

    @Override
    public void onDisable() {
        if (eventManager != null) eventManager.forceStop();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!cmd.getName().equalsIgnoreCase("volcano")) return false;
        if (!sender.hasPermission("volcano.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (eventManager.isRunning()) { sender.sendMessage("§cEvent already running."); return true; }
                if (eventManager.getVolcanoPoint() == null) {
                    sender.sendMessage("§cNo volcano point set. Stand at the volcano tip and run §e/volcano setpoint");
                    return true;
                }
                eventManager.startEvent();
                sender.sendMessage("§6[Volcano] Event started.");
            }
            case "stop" -> {
                if (!eventManager.isRunning() && !eventManager.isPaused()) {
                    sender.sendMessage("§cNothing is running.");
                    return true;
                }
                eventManager.forceStop();
                sender.sendMessage("§a[Volcano] Event stopped.");
            }
            case "skip" -> {
                if (!eventManager.isRunning()) { sender.sendMessage("§cEvent not running."); return true; }
                eventManager.skipPhase();
                sender.sendMessage("§e[Volcano] Phase skipped.");
            }
            case "pause" -> {
                if (!eventManager.isRunning() && !eventManager.isPaused()) {
                    sender.sendMessage("§cEvent not running.");
                    return true;
                }
                if (eventManager.isPaused()) {
                    eventManager.resumeEvent();
                    sender.sendMessage("§a[Volcano] Event resumed.");
                } else {
                    eventManager.pauseEvent();
                    sender.sendMessage("§e[Volcano] Event paused.");
                }
            }
            case "setpoint" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cIn-game only."); return true; }
                eventManager.setVolcanoPoint(p.getLocation());
                sender.sendMessage("§a[Volcano] Volcano point set to §e"
                        + (int)p.getX() + ", " + (int)p.getY() + ", " + (int)p.getZ()
                        + " §ain world §e" + p.getWorld().getName());
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (!cmd.getName().equalsIgnoreCase("volcano")) return Collections.emptyList();
        if (!sender.hasPermission("volcano.admin")) return Collections.emptyList();
        if (args.length == 1) {
            String typed = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(typed))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§6--- Volcano Commands ---");
        s.sendMessage("§e/volcano setpoint    §7Set eruption origin (stand at volcano tip)");
        s.sendMessage("§e/volcano start       §7Start the event");
        s.sendMessage("§e/volcano stop        §7Stop the event");
        s.sendMessage("§e/volcano skip        §7Skip current phase");
        s.sendMessage("§e/volcano pause       §7Pause / resume the event");
    }

    public EventManager getEventManager() { return eventManager; }
}
