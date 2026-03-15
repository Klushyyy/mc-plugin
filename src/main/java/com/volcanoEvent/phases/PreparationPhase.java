package com.volcanoEvent.phases;

import com.volcanoEvent.VolcanoEventPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.Random;

public class PreparationPhase extends PhaseBase {

    private final Random rng = new Random();
    private int tick = 0;

    public PreparationPhase(VolcanoEventPlugin plugin, Location volcano) {
        super(plugin, volcano);
    }

    @Override
    public void start() {
        // Light smoke rising from tip, increasing over time
        every(0, 12, () -> {
            tick++;
            int count = Math.min(1 + tick / 800, 6);
            for (int i = 0; i < count; i++) {
                double ox = (rng.nextDouble() - 0.5) * 4;
                double oz = (rng.nextDouble() - 0.5) * 4;
                Location l = volcano.clone().add(ox, rng.nextDouble() * 6, oz);
                volcano.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, l,
                        1, 0.3, 0.4, 0.3, 0.02);
            }
        });

        // Distant low rumble every 30 seconds
        every(0, 20 * 30, () -> {
            for (Player p : nearby(1200)) {
                p.playSound(volcano, Sound.ENTITY_RAVAGER_STEP,
                        SoundCategory.AMBIENT, 0.5f, 0.3f + rng.nextFloat() * 0.2f);
            }
        });

        // Timed broadcast messages — plain and informative
        later(20 * 60 * 1,  () -> broadcast("§7[VOLCANO] Seismic activity detected beneath the island."));
        later(20 * 60 * 3,  () -> broadcast("§7[VOLCANO] Sulfur levels rising. The volcano is active."));
        later(20 * 60 * 5,  () -> {
            broadcast("§e[VOLCANO] Minor tremors reported across the island.");
            title("§ePhase 1", "§7Preparation — tremors detected");
        });
        later(20 * 60 * 8,  () -> broadcast("§7[VOLCANO] Smoke visible from the caldera."));
        later(20 * 60 * 10, () -> {
            broadcast("§c[VOLCANO] Seismic activity escalating. Prepare now.");
            title("§c⚠ Warning", "§7Seismic escalation");
        });
        later(20 * 60 * 15, () -> {
            broadcast("§c[VOLCANO] 15 minutes until eruption. Secure supplies and find high ground.");
            title("§c15 Minutes", "§7Eruption imminent");
        });
        later(20 * 60 * 20, () -> broadcast("§c[VOLCANO] Ground fractures spreading outward from the caldera."));
        later(20 * 60 * 23, () -> broadcast("§c[VOLCANO] Lava visible at the crater rim."));
        later(20 * 60 * 25, () -> {
            broadcast("§4[VOLCANO] 5 minutes until earthquake. Seek shelter.");
            title("§4§l5 Minutes", "§cEarthquake incoming");
        });
        later(20 * 60 * 27, () -> broadcast("§4[VOLCANO] Structural damage expected across the island."));
        later(20 * 60 * 29, () -> {
            broadcast("§4[VOLCANO] 60 seconds until earthquake.");
            title("§4§l60 Seconds", "§cTake cover");
        });
    }
}
