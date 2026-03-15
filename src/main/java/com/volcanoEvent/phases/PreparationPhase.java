package com.volcanoEvent.phases;

import com.volcanoEvent.VolcanoEventPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Random;

public class PreparationPhase extends PhaseBase {

    private final Random rng = new Random();
    private int tick = 0;

    public PreparationPhase(VolcanoEventPlugin plugin, Location volcano) {
        super(plugin, volcano);
    }

    @Override
    public void start() {
        // Distant low rumble every 30s
        every(0, 20 * 30, () -> {
            for (Player p : nearby(1200)) {
                p.playSound(volcano, Sound.ENTITY_RAVAGER_STEP, SoundCategory.AMBIENT, 0.6f, 0.3f + rng.nextFloat() * 0.2f);
                p.playSound(volcano, Sound.BLOCK_DEEPSLATE_BREAK, SoundCategory.AMBIENT, 0.4f, 0.4f);
            }
        });

        // Light smoke wisps from the tip — low particle count for perf
        every(0, 12, () -> {
            tick++;
            // Increase smoke over time to build tension
            int count = Math.min(1 + tick / 1000, 5);
            for (int i = 0; i < count; i++) {
                double ox = (rng.nextDouble() - 0.5) * 4;
                double oz = (rng.nextDouble() - 0.5) * 4;
                Location l = volcano.clone().add(ox, rng.nextDouble() * 5, oz);
                volcano.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, l, 1,
                        0.3, 0.5, 0.3, 0.02);
            }
        });

        // Periodic tension messages throughout 30 minutes
        later(20 * 60 * 1,  () -> broadcast("§7[Volcano] A faint tremor rattles the ground beneath your feet..."));
        later(20 * 60 * 3,  () -> broadcast("§7[Volcano] The air smells of sulfur. Something is coming."));
        later(20 * 60 * 5,  () -> {
            broadcast("§e[Volcano] The volcano is showing signs of activity. Be prepared.");
            title("§eWarning", "§7Volcanic activity detected");
        });
        later(20 * 60 * 8,  () -> broadcast("§7[Volcano] A thin column of smoke rises from the volcano tip..."));
        later(20 * 60 * 10, () -> {
            broadcast("§c[Volcano] The ground rumbles more frequently now. Take stock of your supplies!");
            title("§c⚠ WARNING", "§7Seismic activity increasing");
        });
        later(20 * 60 * 13, () -> broadcast("§7[Volcano] Animals on the island are fleeing inland. Not a good sign."));
        later(20 * 60 * 15, () -> {
            broadcast("§c§l[Volcano] HALFWAY — The eruption countdown has begun. 15 minutes remain!");
            title("§c§l15 MINUTES", "§7Find shelter or high ground NOW");
        });
        later(20 * 60 * 18, () -> broadcast("§c[Volcano] The tremors are growing violent. Windows are rattling across the island."));
        later(20 * 60 * 20, () -> {
            broadcast("§4[Volcano] The volcano is groaning loudly. Eruption is imminent!");
            // Give everyone a brief nausea hint of what's coming
            for (Player p : nearby(1200))
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, false, false, false));
        });
        later(20 * 60 * 23, () -> broadcast("§4[Volcano] Lava has been spotted bubbling at the caldera rim!"));
        later(20 * 60 * 25, () -> {
            broadcast("§4§l[Volcano] 5 MINUTES — Secure yourself. The earthquake begins soon!");
            title("§4§l5 MINUTES", "§cEarthquake imminent!");
            for (Player p : nearby(1200))
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, SoundCategory.AMBIENT, 0.5f, 0.4f);
        });
        later(20 * 60 * 27, () -> broadcast("§4[Volcano] The very air vibrates. Run. Shelter. Pray."));
        later(20 * 60 * 29, () -> {
            broadcast("§4§l[Volcano] 60 SECONDS UNTIL EARTHQUAKE!");
            title("§4§l60 SECONDS", "§cFind cover!");
            for (Player p : nearby(1200))
                p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 30, 0, false, false, false));
        });
    }
}
