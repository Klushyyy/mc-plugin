package com.volcanoEvent.phases;

import com.volcanoEvent.VolcanoEventPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class EruptionPhase extends PhaseBase {

    private static final double RADIUS = 800;
    private final Random rng = new Random();

    // Lava arc chunks — FallingBlock lava shot upward like a fountain
    private final List<org.bukkit.entity.Entity> lavaChunks = new ArrayList<>();

    private static final Material[] VOLCANIC_SURFACE = {
        Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
        Material.GRAVEL, Material.SAND, Material.STONE, Material.AIR
    };

    public EruptionPhase(VolcanoEventPlugin plugin, Location volcano) {
        super(plugin, volcano);
    }

    @Override
    public void start() {
        title("§cPhase 3", "§fEruption");
        broadcast("§c[VOLCANO] Phase 3: Eruption. Move away from the volcano.");

        // Lava fountain — FallingBlock magma chunks arc upward then outward
        every(0, 6, this::fountainBurst);

        // Smoke column rising high above volcano
        every(0, 5, this::smokeColumn);

        // Lava spread on ground near base — players walking into it get burned naturally
        every(20 * 20, 20 * 25, this::spreadLavaOnGround);

        // Fire rain particles (visual only — no forced damage)
        every(0, 8, this::fireRainParticles);

        // Ash increasing — slowness only (visual gameplay effect, no damage)
        every(0, 30, () -> {
            for (Player p : nearby(RADIUS)) {
                double dist = p.getLocation().distance(volcano);
                if (dist < 300) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            35, 0, false, false, false));
                }
            }
        });

        // Sounds
        every(0, 20, () -> {
            for (Player p : nearby(RADIUS)) {
                p.playSound(volcano, Sound.ENTITY_BLAZE_HURT,
                        SoundCategory.AMBIENT, 1.3f, 0.4f + rng.nextFloat() * 0.3f);
                p.playSound(volcano, Sound.BLOCK_LAVA_POP,
                        SoundCategory.AMBIENT, 1.0f, 0.7f);
            }
        });
        every(0, 55, () -> {
            for (Player p : nearby(RADIUS))
                p.playSound(volcano, Sound.ENTITY_WARDEN_ROAR,
                        SoundCategory.AMBIENT, 0.3f, 0.3f);
        });

        later(20 * 30,  () -> broadcast("§c[VOLCANO] Lava spreading from the volcano base."));
        later(20 * 150, () -> broadcast("§c[VOLCANO] Lava flows are expanding."));
        later(20 * 250, () -> broadcast("§c[VOLCANO] Pyroclastic surge incoming."));
    }

    /**
     * Shoots MAGMA_BLOCK FallingBlocks upward in arcs from the volcano tip.
     * They arc up ~20 blocks then fall outward — landing sets fire.
     */
    private void fountainBurst() {
        int count = 3 + rng.nextInt(3);
        for (int i = 0; i < count; i++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double spread = rng.nextDouble() * 2.5;

            // Random arc: up-first, then outward
            double vx = Math.cos(angle) * spread * 0.07;
            double vy = 0.55 + rng.nextDouble() * 0.35; // upward force
            double vz = Math.sin(angle) * spread * 0.07;

            Location origin = volcano.clone().add(
                    (rng.nextDouble() - 0.5) * 3, 0,
                    (rng.nextDouble() - 0.5) * 3);

            FallingBlock fb = volcano.getWorld().spawnFallingBlock(
                    origin, Material.MAGMA_BLOCK.createBlockData());
            fb.setVelocity(new Vector(vx, vy, vz));
            fb.setDropItem(false);
            fb.setHurtEntities(true);
            lavaChunks.add(fb);

            // Trail particles
            plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
                if (!fb.isValid()) { task.cancel(); return; }
                fb.getWorld().spawnParticle(Particle.FLAME, fb.getLocation(),
                        2, 0.1, 0.1, 0.1, 0.01);
                fb.getWorld().spawnParticle(Particle.LAVA, fb.getLocation(),
                        1, 0, 0, 0, 0);
            }, 0, 2);
        }

        // Big lava particle burst from tip every burst
        volcano.getWorld().spawnParticle(Particle.LAVA, volcano, 8, 3, 0.5, 3);
        volcano.getWorld().spawnParticle(Particle.DRIPPING_LAVA, volcano, 10, 4, 1, 4);
    }

    private void smokeColumn() {
        double h = 8 + rng.nextDouble() * 35;
        Location top = volcano.clone().add(
                (rng.nextDouble() - 0.5) * 4, h, (rng.nextDouble() - 0.5) * 4);
        volcano.getWorld().spawnParticle(Particle.LARGE_SMOKE, top, 2, 2, 1, 2, 0.03);
        volcano.getWorld().spawnParticle(Particle.SMOKE, top, 3, 1.5, 0.8, 1.5, 0.04);
        if (rng.nextInt(3) == 0)
            volcano.getWorld().spawnParticle(Particle.FLAME, top, 1, 0.5, 0.2, 0.5, 0.01);
    }

    /**
     * Places real lava blocks on volcanic-type ground near base — players
     * naturally take lava damage if they walk into it. No forced proximity damage.
     */
    private void spreadLavaOnGround() {
        int waveRadius = 6 + (rng.nextInt(3) * 5);
        for (int i = 0; i < 10; i++) {
            double a = (Math.PI * 2 / 10) * i + rng.nextDouble() * 0.4;
            double r = waveRadius + rng.nextDouble() * 4;
            double x = volcano.getX() + Math.cos(a) * r;
            double z = volcano.getZ() + Math.sin(a) * r;
            int gy = volcano.getWorld().getHighestBlockYAt((int) x, (int) z);
            Block b = volcano.getWorld().getBlockAt((int) x, gy, (int) z);
            if (isVolcanicSurface(b.getType())) {
                b.setType(Material.LAVA);
            }
        }
    }

    private void fireRainParticles() {
        for (Player p : nearby(600)) {
            double dist = p.getLocation().distance(volcano);
            int cnt = dist < 200 ? 3 : (dist < 400 ? 2 : 1);
            for (int i = 0; i < cnt; i++) {
                double ox = (rng.nextDouble() - 0.5) * 16;
                double oz = (rng.nextDouble() - 0.5) * 16;
                Location drop = p.getLocation().clone().add(ox, 9 + rng.nextDouble() * 5, oz);
                p.getWorld().spawnParticle(Particle.FLAME, drop, 1, 0, -0.12, 0, 0.04);
            }
        }
    }

    private boolean isVolcanicSurface(Material m) {
        for (Material a : VOLCANIC_SURFACE) if (a == m) return true;
        return false;
    }

    @Override
    protected void onStop() {
        for (org.bukkit.entity.Entity e : lavaChunks)
            if (e.isValid()) e.remove();
        lavaChunks.clear();
    }
}
