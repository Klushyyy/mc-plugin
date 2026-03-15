package com.volcanoEvent.phases;

import com.volcanoEvent.VolcanoEventPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class PhaseBase {

    protected final VolcanoEventPlugin plugin;
    protected final Location volcano; // tip of the volcano
    protected final List<BukkitTask> tasks = new ArrayList<>();

    public PhaseBase(VolcanoEventPlugin plugin, Location volcano) {
        this.plugin = plugin;
        this.volcano = volcano.clone();
    }

    /** Called to begin the phase. */
    public abstract void start();

    /** Called to cleanly end the phase. */
    public void stop() {
        cancelTasks();
        onStop();
    }

    protected void cancelTasks() {
        for (BukkitTask t : tasks) t.cancel();
        tasks.clear();
    }

    /** Override for cleanup of placed blocks, entities etc. */
    protected void onStop() {}

    // ---- Scheduling helpers ----

    protected void later(long ticks, Runnable r) {
        tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin, r, ticks));
    }

    protected void every(long delay, long period, Runnable r) {
        tasks.add(plugin.getServer().getScheduler().runTaskTimer(plugin, r, delay, period));
    }

    // ---- World helpers ----

    /** All online players within radius of the volcano tip. */
    protected List<Player> nearby(double radius) {
        List<Player> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getWorld().equals(volcano.getWorld())
                    && p.getLocation().distanceSquared(volcano) <= r2) {
                out.add(p);
            }
        }
        return out;
    }

    protected void broadcast(String msg) {
        plugin.getServer().broadcastMessage(msg);
    }

    protected void title(String title, String sub) {
        for (Player p : plugin.getServer().getOnlinePlayers())
            p.sendTitle(title, sub, 10, 50, 20);
    }
}
