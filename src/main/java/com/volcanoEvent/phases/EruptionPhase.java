package com.volcanoEvent.phases;

import com.volcanoEvent.VolcanoEventPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class EruptionPhase extends PhaseBase {

    private static final double RADIUS = 800;
    private final Random rng = new Random();
    private final List<Location> placedLava = new ArrayList<>();

    private static final Material[] VOLCANIC_GROUND = {
            Material.GRASS_BLOCK, Material.DIRT, Material.GRAVEL,
            Material.SAND, Material.STONE, Material.AIR, Material.COARSE_DIRT
    };

    public EruptionPhase(VolcanoEventPlugin plugin, Location volcano) {
        super(plugin, volcano);
    }

    @Override
    public void start() {
        title("§c🌋 ERUPTION", "§6Lava spews from the volcano!");
        broadcast("§c§l[Volcano] PHASE 3 — LAVA ERUPTION! Get away from the volcano!");

        // Lava geyser particles from tip
        every(0, 4, this::lavaGeyser);

        // Big smoke column above volcano
        every(0, 6, this::smokeColumn);

        // Heat + fire damage scaled by distance
        every(0, 20, this::applyHeat);

        // Lava spread outward from base over time
        every(20 * 15, 20 * 20, this::spreadLava);

        // Fire rain on players
        every(0, 8, this::fireRain);

        // Eruption sounds
        every(0, 25, () -> {
            for (Player p : nearby(RADIUS)) {
                p.playSound(volcano, Sound.ENTITY_BLAZE_HURT, SoundCategory.AMBIENT,
                        1.5f, 0.4f + rng.nextFloat() * 0.3f);
                p.playSound(volcano, Sound.BLOCK_LAVA_POP, SoundCategory.AMBIENT, 1.2f, 0.7f);
            }
        });

        every(0, 60, () -> {
            for (Player p : nearby(RADIUS))
                p.playSound(volcano, Sound.ENTITY_WARDEN_ROAR, SoundCategory.AMBIENT, 0.4f, 0.3f);
        });

        later(20 * 30,  () -> broadcast("§c[Volcano] Lava flows are spreading outward — move to higher ground!"));
        later(20 * 120, () -> broadcast("§c[Volcano] The air is thick with ash and heat. Breathing is difficult."));
        later(20 * 240, () -> broadcast("§4[Volcano] The eruption shows no sign of stopping!"));
    }

    private void lavaGeyser() {
        for (int i = 0; i < 10; i++) {
            double ox = (rng.nextDouble() - 0.5) * 5;
            double oz = (rng.nextDouble() - 0.5) * 5;
            Location l = volcano.clone().add(ox, 0.5, oz);
            volcano.getWorld().spawnParticle(Particle.LAVA, l, 1,
                    (rng.nextDouble()-0.5)*0.15,
                    0.35 + rng.nextDouble()*0.4,
                    (rng.nextDouble()-0.5)*0.15, 0);
            volcano.getWorld().spawnParticle(Particle.FLAME, l, 1,
                    (rng.nextDouble()-0.5)*0.2, 0.4, (rng.nextDouble()-0.5)*0.2, 0.06);
        }
        volcano.getWorld().spawnParticle(Particle.DRIPPING_LAVA, volcano, 8, 4, 0.5, 4);
    }

    private void smokeColumn() {
        double h = 5 + rng.nextDouble() * 30;
        Location top = volcano.clone().add((rng.nextDouble()-0.5)*3, h, (rng.nextDouble()-0.5)*3);
        volcano.getWorld().spawnParticle(Particle.LARGE_SMOKE, top, 2, 2, 1, 2, 0.03);
        volcano.getWorld().spawnParticle(Particle.SMOKE, top, 3, 1.5, 0.8, 1.5, 0.04);
        // Ember sparks rising
        if (rng.nextInt(3) == 0)
            volcano.getWorld().spawnParticle(Particle.FLAME, top, 1, 0.5, 0.2, 0.5, 0.01);
    }

    private void applyHeat() {
        for (Player p : nearby(RADIUS)) {
            double dist = p.getLocation().distance(volcano);

            if (dist < 80) {
                // Direct lava zone — lethal heat
                p.damage(3.0);
                p.setFireTicks(100);
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));
            } else if (dist < 200) {
                // Inner heat ring
                if (rng.nextInt(2) == 0) {
                    p.damage(1.5);
                    p.setFireTicks(60);
                }
            } else if (dist < 400) {
                // Warm zone — occasional ember damage
                if (rng.nextInt(4) == 0) {
                    p.damage(0.5);
                    p.setFireTicks(20);
                }
            }

            // Breathing difficulty (mining fatigue = visual oppression)
            if (dist < 500) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 30, 0, false, false, false));
            }

            // Universal ash haze
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 0, false, false, false));
        }
    }

    private void spreadLava() {
        // Place a ring of lava blocks around the volcano base, crawling outward
        int ring = (placedLava.size() / 12) + 1;
        double baseRadius = 5 + ring * 4;
        int attempts = 12;
        for (int i = 0; i < attempts; i++) {
            double angle = (Math.PI * 2 / attempts) * i + rng.nextDouble() * 0.5;
            double r = baseRadius + rng.nextDouble() * 6;
            double x = volcano.getX() + Math.cos(angle) * r;
            double z = volcano.getZ() + Math.sin(angle) * r;
            int groundY = volcano.getWorld().getHighestBlockYAt((int)x, (int)z);
            Block b = volcano.getWorld().getBlockAt((int)x, groundY, (int)z);
            if (isVolcanicSurface(b.getType())) {
                b.setType(Material.LAVA);
                placedLava.add(b.getLocation());
            }
        }
    }

    private void fireRain() {
        for (Player p : nearby(600)) {
            double dist = p.getLocation().distance(volcano);
            int count = dist < 150 ? 4 : (dist < 350 ? 2 : 1);
            for (int i = 0; i < count; i++) {
                double ox = (rng.nextDouble()-0.5)*18;
                double oz = (rng.nextDouble()-0.5)*18;
                Location drop = p.getLocation().clone().add(ox, 10 + rng.nextDouble()*6, oz);
                p.getWorld().spawnParticle(Particle.FLAME, drop, 1, 0, -0.15, 0, 0.04);
            }
        }
    }

    private boolean isVolcanicSurface(Material m) {
        for (Material allowed : VOLCANIC_GROUND) if (allowed == m) return true;
        return false;
    }

    @Override
    protected void onStop() {
        // Leave lava in world intentionally — flows should persist.
        // But if you want cleanup: uncomment below
        // for (Location l : placedLava) if (l.getBlock().getType()==Material.LAVA) l.getBlock().setType(Material.AIR);
        placedLava.clear();
    }
}
