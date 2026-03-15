package com.volcanoEvent.managers;

import com.volcanoEvent.VolcanoEventPlugin;
import com.volcanoEvent.phases.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class EventManager {

    private final VolcanoEventPlugin plugin;
    private Location volcanoPoint;

    // State
    private boolean running = false;
    private boolean paused  = false;
    private int currentPhaseIndex = -1;
    private long phaseTicksRemaining = 0;
    private long pausedTicksRemaining = 0;

    // Active phase instance
    private PhaseBase currentPhase = null;

    // Bossbar
    private BossBar bossBar = null;

    // Scheduler tasks
    private BukkitTask countdownTask = null;
    private BukkitTask phaseEndTask  = null;

    // Ash particle task (runs across all phases)
    private BukkitTask ashTask = null;

    // Phase definitions: name, colour, duration in seconds
    private record PhaseDef(String name, BossBar.Color barColor, int durationSeconds) {}

    private final PhaseDef[] PHASES = {
        new PhaseDef("§e☁ PHASE 1 — PREPARATION",       BossBar.Color.YELLOW,  30 * 60),
        new PhaseDef("§6⚡ PHASE 2 — EARTHQUAKE",        BossBar.Color.YELLOW,   2 * 60),
        new PhaseDef("§c🌋 PHASE 3 — ERUPTION",          BossBar.Color.RED,      5 * 60),
        new PhaseDef("§4💥 PHASE 4 — PYROCLASTIC SURGE", BossBar.Color.RED,     10 * 60),
        new PhaseDef("§4👹 PHASE 5 — INFERNO ASSAULT",   BossBar.Color.PINK,    10 * 60),
    };

    public EventManager(VolcanoEventPlugin plugin) {
        this.plugin = plugin;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public void startEvent() {
        running = true;
        paused  = false;
        currentPhaseIndex = -1;

        // Create boss bar visible to all current + future players
        bossBar = BossBar.bossBar(
                Component.text("🌋 Volcano Event", NamedTextColor.GOLD, TextDecoration.BOLD),
                1.0f,
                BossBar.Color.YELLOW,
                BossBar.Overlay.PROGRESS
        );
        for (Player p : plugin.getServer().getOnlinePlayers()) p.showBossBar(bossBar);

        // Start persistent ash particles
        startAsh();

        broadcastAll("§6§l[VOLCANO] §eThe volcano is awakening — a cataclysmic event begins!");
        titleAll("§6☄ VOLCANO EVENT", "§eSomething stirs beneath the earth...");

        advancePhase();
    }

    public void skipPhase() {
        if (!running) return;
        stopCurrentPhase();
        if (phaseEndTask != null) { phaseEndTask.cancel(); phaseEndTask = null; }
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        advancePhase();
    }

    public void pauseEvent() {
        if (!running || paused) return;
        paused = true;
        pausedTicksRemaining = phaseTicksRemaining;
        if (phaseEndTask  != null) { phaseEndTask.cancel();  phaseEndTask  = null; }
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (currentPhase  != null) currentPhase.cancelTasks();
        if (ashTask       != null) { ashTask.cancel(); ashTask = null; }
        if (bossBar != null) bossBar.name(Component.text("🌋 PAUSED — " + PHASES[currentPhaseIndex].name(), NamedTextColor.GRAY));
        broadcastAll("§e§l[VOLCANO] §7The event has been paused.");
    }

    public void resumeEvent() {
        if (!paused) return;
        paused  = false;
        running = true;
        phaseTicksRemaining = pausedTicksRemaining;
        startAsh();
        // Restart current phase
        launchPhase(currentPhaseIndex, phaseTicksRemaining);
        broadcastAll("§a§l[VOLCANO] §7The event has resumed!");
    }

    public void forceStop() {
        stopCurrentPhase();
        if (phaseEndTask  != null) { phaseEndTask.cancel();  phaseEndTask  = null; }
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (ashTask       != null) { ashTask.cancel(); ashTask = null; }
        if (bossBar != null) {
            for (Player p : plugin.getServer().getOnlinePlayers()) p.hideBossBar(bossBar);
            bossBar = null;
        }
        running = false;
        paused  = false;
        currentPhaseIndex = -1;
        broadcastAll("§c§l[VOLCANO] §7The volcano event has been stopped.");
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private void advancePhase() {
        currentPhaseIndex++;
        if (currentPhaseIndex >= PHASES.length) {
            endEvent();
            return;
        }
        PhaseDef def = PHASES[currentPhaseIndex];
        launchPhase(currentPhaseIndex, def.durationSeconds * 20L);
    }

    private void launchPhase(int index, long durationTicks) {
        PhaseDef def = PHASES[index];
        phaseTicksRemaining = durationTicks;

        // Announce
        titleAll(def.name(), "§7" + formatTime((int)(durationTicks / 20)) + " remaining");
        broadcastAll("§6§l[VOLCANO] " + def.name() + " §7begins! ("
                + formatTime((int)(durationTicks / 20)) + ")");

        // Update boss bar colour
        if (bossBar != null) bossBar.color(def.barColor());

        // Start the actual phase logic
        stopCurrentPhase();
        currentPhase = buildPhase(index);
        if (currentPhase != null) currentPhase.start();

        // Countdown ticker (every second)
        long totalTicks = durationTicks;
        countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            phaseTicksRemaining -= 20;
            if (phaseTicksRemaining < 0) phaseTicksRemaining = 0;

            // Bossbar progress
            if (bossBar != null) {
                float progress = (float) phaseTicksRemaining / totalTicks;
                bossBar.progress(Math.max(0f, Math.min(1f, progress)));
                int secsLeft = (int)(phaseTicksRemaining / 20);
                bossBar.name(Component.text(
                        "🌋 " + stripColor(def.name()) + " — " + formatTime(secsLeft),
                        TextColor.color(255, 140, 0),
                        TextDecoration.BOLD
                ));
            }

            // Milestone warns
            int secsLeft = (int)(phaseTicksRemaining / 20);
            if (secsLeft == 60)  broadcastAll("§c§l[VOLCANO] §e1 minute until next phase!");
            if (secsLeft == 30)  broadcastAll("§c§l[VOLCANO] §c30 seconds until next phase!");
            if (secsLeft == 10)  broadcastAll("§4§l[VOLCANO] §c10 seconds!");
            if (secsLeft <= 5 && secsLeft > 0) broadcastAll("§4§l[VOLCANO] §c" + secsLeft + "...");

        }, 20, 20);

        // Schedule phase end
        phaseEndTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
            advancePhase();
        }, durationTicks);
    }

    private PhaseBase buildPhase(int index) {
        return switch (index) {
            case 0 -> new PreparationPhase(plugin, volcanoPoint);
            case 1 -> new EarthquakePhase(plugin, volcanoPoint);
            case 2 -> new EruptionPhase(plugin, volcanoPoint);
            case 3 -> new PyroclasticPhase(plugin, volcanoPoint);
            case 4 -> new InfernoMobPhase(plugin, volcanoPoint);
            default -> null;
        };
    }

    private void stopCurrentPhase() {
        if (currentPhase != null) {
            currentPhase.stop();
            currentPhase = null;
        }
    }

    private void endEvent() {
        if (ashTask != null) { ashTask.cancel(); ashTask = null; }
        if (bossBar != null) {
            bossBar.name(Component.text("🌋 Volcano Event — OVER", NamedTextColor.GREEN));
            bossBar.progress(1.0f);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (Player p : plugin.getServer().getOnlinePlayers()) p.hideBossBar(bossBar);
                bossBar = null;
            }, 20 * 10);
        }
        running = false;
        currentPhaseIndex = -1;
        broadcastAll("§a§l[VOLCANO] §7The volcano has calmed. Survivors celebrate their victory!");
        titleAll("§a✔ EVENT COMPLETE", "§7The island is safe... for now.");
    }

    /** Persistent background ash that runs during the whole event. Low-cost. */
    private void startAsh() {
        if (ashTask != null) ashTask.cancel();
        // Fires every 8 ticks; light enough for low-end machines
        ashTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (volcanoPoint == null) return;
            // Ash column rising from the volcano tip
            for (int i = 0; i < 6; i++) {
                double ox = (Math.random() - 0.5) * 8;
                double oz = (Math.random() - 0.5) * 8;
                double oy = Math.random() * 20;
                Location l = volcanoPoint.clone().add(ox, oy, oz);
                l.getWorld().spawnParticle(
                        org.bukkit.Particle.ASH, l, 2,
                        1.5, 2.0, 1.5, 0.04);
            }
            // Ash drifting over players in the area
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!p.getWorld().equals(volcanoPoint.getWorld())) continue;
                if (p.getLocation().distanceSquared(volcanoPoint) > 1000 * 1000) continue;
                p.getWorld().spawnParticle(
                        org.bukkit.Particle.ASH,
                        p.getEyeLocation().add(
                                (Math.random()-0.5)*10,
                                3 + Math.random()*4,
                                (Math.random()-0.5)*10),
                        1, 0.5, 0.5, 0.5, 0.02);
            }
        }, 0, 8);
    }

    // -----------------------------------------------------------------------
    // Bossbar player join/leave handling — call from event listener if desired
    // -----------------------------------------------------------------------
    public void onPlayerJoin(Player p) {
        if (bossBar != null) p.showBossBar(bossBar);
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    private String stripColor(String s) {
        return s.replaceAll("§.", "");
    }

    private void broadcastAll(String msg) {
        plugin.getServer().broadcastMessage(msg);
    }

    private void titleAll(String title, String sub) {
        for (Player p : plugin.getServer().getOnlinePlayers())
            p.sendTitle(title, sub, 10, 50, 20);
    }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------
    public boolean isRunning()  { return running; }
    public boolean isPaused()   { return paused; }
    public Location getVolcanoPoint()             { return volcanoPoint; }
    public void     setVolcanoPoint(Location loc) { this.volcanoPoint = loc.clone(); }
}
