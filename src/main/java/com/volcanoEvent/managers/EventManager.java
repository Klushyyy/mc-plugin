package com.volcanoEvent.managers;

import com.volcanoEvent.VolcanoEventPlugin;
import com.volcanoEvent.phases.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class EventManager {

    private final VolcanoEventPlugin plugin;
    private Location volcanoPoint;

    private boolean running = false;
    private boolean paused  = false;
    private int     currentPhaseIndex = -1;
    private long    phaseTicksRemaining = 0;
    private long    pausedTicksRemaining = 0;

    private PhaseBase  currentPhase  = null;
    private BossBar    bossBar       = null;
    private BukkitTask countdownTask = null;
    private BukkitTask phaseEndTask  = null;
    private BukkitTask ashTask       = null;

    private final List<BukkitTask> scheduledPhaseStarts = new ArrayList<>();

    private record PhaseDef(String displayName, BossBar.Color barColor, int durationSeconds) {}

    private final PhaseDef[] PHASES = {
        new PhaseDef("Preparation",       BossBar.Color.YELLOW, 30 * 60),
        new PhaseDef("Earthquake",         BossBar.Color.YELLOW,  2 * 60),
        new PhaseDef("Eruption",           BossBar.Color.RED,     5 * 60),
        new PhaseDef("Pyroclastic Surge",  BossBar.Color.RED,    10 * 60),
        new PhaseDef("Inferno Assault",    BossBar.Color.PINK,   10 * 60),
    };

    public EventManager(VolcanoEventPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void startEvent() {
        running = true;
        paused  = false;
        currentPhaseIndex = -1;

        bossBar = BossBar.bossBar(
                Component.text("🌋 Volcano Event", NamedTextColor.GOLD, TextDecoration.BOLD),
                1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        for (Player p : plugin.getServer().getOnlinePlayers()) p.showBossBar(bossBar);

        startAsh();
        broadcast("§6[VOLCANO] The volcano event has begun.");
        advancePhase();
    }

    public void skipPhase() {
        if (!running) return;
        stopCurrentPhase();
        cancelCountdown();
        advancePhase();
    }

    public void pauseEvent() {
        if (!running || paused) return;
        paused = true;
        pausedTicksRemaining = phaseTicksRemaining;
        cancelCountdown();
        if (currentPhase != null) currentPhase.cancelTasks();
        stopAsh();
        if (bossBar != null)
            bossBar.name(Component.text("🌋 PAUSED — " + PHASES[currentPhaseIndex].displayName(), NamedTextColor.GRAY));
        broadcast("§e[VOLCANO] Event paused.");
    }

    public void resumeEvent() {
        if (!paused) return;
        paused  = false;
        running = true;
        phaseTicksRemaining = pausedTicksRemaining;
        startAsh();
        launchPhase(currentPhaseIndex, phaseTicksRemaining);
        broadcast("§a[VOLCANO] Event resumed.");
    }

    public void forceStop() {
        stopCurrentPhase();
        cancelCountdown();
        stopAsh();
        for (BukkitTask t : scheduledPhaseStarts) t.cancel();
        scheduledPhaseStarts.clear();
        if (bossBar != null) {
            for (Player p : plugin.getServer().getOnlinePlayers()) p.hideBossBar(bossBar);
            bossBar = null;
        }
        running = false;
        paused  = false;
        currentPhaseIndex = -1;
        broadcast("§c[VOLCANO] Event stopped.");
    }

    public void onPlayerJoin(Player p) {
        if (bossBar != null) p.showBossBar(bossBar);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void advancePhase() {
        currentPhaseIndex++;
        if (currentPhaseIndex >= PHASES.length) { endEvent(); return; }
        launchPhase(currentPhaseIndex, PHASES[currentPhaseIndex].durationSeconds() * 20L);
    }

    private void launchPhase(int index, long durationTicks) {
        PhaseDef def = PHASES[index];
        phaseTicksRemaining = durationTicks;
        long totalTicks = durationTicks;

        // Announce
        String timeStr = formatTime((int)(durationTicks / 20));
        for (Player p : plugin.getServer().getOnlinePlayers())
            p.sendTitle("§6Phase " + (index + 1), "§f" + def.displayName(), 10, 50, 20);
        broadcast("§6[VOLCANO] Phase " + (index + 1) + ": §f" + def.displayName()
                + " §7(" + timeStr + ")");

        if (bossBar != null) bossBar.color(def.barColor());

        // Start phase logic
        stopCurrentPhase();
        currentPhase = buildPhase(index);
        if (currentPhase != null) currentPhase.start();

        // Countdown every second
        countdownTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            phaseTicksRemaining -= 20;
            if (phaseTicksRemaining < 0) phaseTicksRemaining = 0;

            if (bossBar != null) {
                float progress = Math.max(0f, Math.min(1f, (float) phaseTicksRemaining / totalTicks));
                bossBar.progress(progress);
                int secs = (int)(phaseTicksRemaining / 20);
                bossBar.name(Component.text(
                        "🌋 Phase " + (index + 1) + ": " + def.displayName() + " — " + formatTime(secs),
                        TextColor.color(255, 140, 0), TextDecoration.BOLD));
            }

            int secs = (int)(phaseTicksRemaining / 20);
            if (secs == 60) broadcast("§e[VOLCANO] 1 minute until next phase.");
            if (secs == 30) broadcast("§c[VOLCANO] 30 seconds until next phase.");
            if (secs <= 5 && secs > 0) broadcast("§4[VOLCANO] " + secs + "...");

        }, 20, 20);

        phaseEndTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            cancelCountdown();
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
        if (currentPhase != null) { currentPhase.stop(); currentPhase = null; }
    }

    private void cancelCountdown() {
        if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        if (phaseEndTask  != null) { phaseEndTask.cancel();  phaseEndTask  = null; }
    }

    private void endEvent() {
        stopAsh();
        if (bossBar != null) {
            bossBar.name(Component.text("🌋 Volcano Event — Complete", NamedTextColor.GREEN));
            bossBar.progress(1.0f);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (Player p : plugin.getServer().getOnlinePlayers()) p.hideBossBar(bossBar);
                bossBar = null;
            }, 20 * 15);
        }
        running = false;
        currentPhaseIndex = -1;
        for (Player p : plugin.getServer().getOnlinePlayers())
            p.sendTitle("§aEvent Complete", "§7The volcano has calmed.", 10, 80, 20);
        broadcast("§a[VOLCANO] The volcano event is over.");
    }

    /** Persistent ash column rising from volcano — runs entire event, optimised. */
    private void startAsh() {
        stopAsh();
        ashTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (volcanoPoint == null) return;
            // Column from tip — 5 particles per tick, very cheap
            for (int i = 0; i < 5; i++) {
                double ox = (Math.random() - 0.5) * 6;
                double oz = (Math.random() - 0.5) * 6;
                double oy = Math.random() * 25;
                Location l = volcanoPoint.clone().add(ox, oy, oz);
                l.getWorld().spawnParticle(Particle.BLOCK,
                        l, 1, 0.5, 0.5, 0.5, 0.01,
                        org.bukkit.Material.GRAY_CONCRETE_POWDER.createBlockData());
            }
            // Drift over nearby players — 1 per player
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!p.getWorld().equals(volcanoPoint.getWorld())) continue;
                if (p.getLocation().distanceSquared(volcanoPoint) > 900 * 900) continue;
                double ox = (Math.random() - 0.5) * 12;
                double oz = (Math.random() - 0.5) * 12;
                Location l = p.getLocation().clone().add(ox, 4 + Math.random() * 5, oz);
                l.getWorld().spawnParticle(Particle.BLOCK,
                        l, 1, 0.3, 0.3, 0.3, 0.005,
                        org.bukkit.Material.GRAY_CONCRETE_POWDER.createBlockData());
            }
        }, 0, 6);
    }

    private void stopAsh() {
        if (ashTask != null) { ashTask.cancel(); ashTask = null; }
    }

    private String formatTime(int totalSec) {
        return String.format("%02d:%02d", totalSec / 60, totalSec % 60);
    }

    private void broadcast(String msg) {
        plugin.getServer().broadcastMessage(msg);
    }

    // ── Getters/Setters ───────────────────────────────────────────────────────
    public boolean  isRunning()                    { return running; }
    public boolean  isPaused()                     { return paused; }
    public Location getVolcanoPoint()              { return volcanoPoint; }
    public void     setVolcanoPoint(Location loc)  { volcanoPoint = loc.clone(); }
}
