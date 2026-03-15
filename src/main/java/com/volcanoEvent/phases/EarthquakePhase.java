package com.volcanoEvent.phases;

import com.volcanoEvent.VolcanoEventPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class EarthquakePhase extends PhaseBase {

    private static final double RADIUS = 900;
    private final Random rng = new Random();
    private int tick = 0;

    // Materials that can be collapsed/holed
    private static final Set<Material> COLLAPSIBLE = Set.of(
        Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
        Material.GRAVEL, Material.SAND, Material.STONE,
        Material.COBBLESTONE, Material.MOSSY_COBBLESTONE,
        Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS,
        Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG
    );

    public EarthquakePhase(VolcanoEventPlugin plugin, Location volcano) {
        super(plugin, volcano);
    }

    @Override
    public void start() {
        title("§6Phase 2", "§fEarthquake");
        broadcast("§6[VOLCANO] Phase 2: Earthquake.");

        // Velocity shake every 5 ticks
        every(0, 5, () -> {
            tick++;
            boolean intense = tick > 20 * 90; // last 30 seconds
            for (Player p : nearby(RADIUS)) shake(p, intense);
        });

        // Launch players into air every 1.5 seconds — avoidable by going indoors
        every(0, 30, () -> {
            for (Player p : nearby(RADIUS)) {
                if (p.isFlying() || p.isInsideVehicle()) continue;
                double dist = p.getLocation().distance(volcano);
                double force = dist < 200 ? 0.5 : (dist < 500 ? 0.3 : 0.15);
                p.setVelocity(p.getVelocity().add(new Vector(0, force, 0)));
            }
        });

        // Ground holes — open sinkholes near players, avoidable by moving
        every(20 * 8, 20 * 8, this::openSinkholes);

        // Ground crack particles
        every(0, 8, this::groundCrackParticles);

        // Weak structure collapse (logs/planks overhead) to prevent boxing in
        every(20, 60, this::collapseWeakRoofs);

        // Sounds
        every(0, 40, () -> {
            for (Player p : nearby(RADIUS)) {
                p.playSound(volcano, Sound.ENTITY_RAVAGER_ROAR,
                        SoundCategory.AMBIENT, 1.5f, 0.3f + rng.nextFloat() * 0.3f);
                p.playSound(volcano, Sound.BLOCK_GRAVEL_BREAK,
                        SoundCategory.AMBIENT, 1.0f, 0.5f);
            }
        });
        every(0, 70, () -> {
            for (Player p : nearby(RADIUS))
                p.playSound(volcano, Sound.ENTITY_WARDEN_SONIC_BOOM,
                        SoundCategory.AMBIENT, 0.25f, 0.3f);
        });

        later(20 * 40,  () -> broadcast("§e[VOLCANO] Sinkholes opening across the island."));
        later(20 * 90,  () -> broadcast("§c[VOLCANO] Earthquake intensifying."));
        later(20 * 100, () -> broadcast("§4[VOLCANO] Eruption imminent."));
    }

    private void shake(Player p, boolean intense) {
        double sx = (rng.nextDouble() - 0.5) * (intense ? 0.25 : 0.13);
        double sz = (rng.nextDouble() - 0.5) * (intense ? 0.25 : 0.13);
        p.setVelocity(p.getVelocity().add(new Vector(sx, 0, sz)));
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, false));
        if (intense)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0, false, false, false));
    }

    /** Opens 2-block deep sinkholes near each player — visible, avoidable. */
    private void openSinkholes() {
        for (Player p : nearby(600)) {
            // Place 1-3 holes around each player at 5-20 block distance
            int holes = 1 + rng.nextInt(3);
            for (int h = 0; h < holes; h++) {
                double angle = rng.nextDouble() * Math.PI * 2;
                double dist  = 5 + rng.nextDouble() * 20;
                double hx = p.getLocation().getX() + Math.cos(angle) * dist;
                double hz = p.getLocation().getZ() + Math.sin(angle) * dist;
                int surfaceY = p.getWorld().getHighestBlockYAt((int) hx, (int) hz);

                // Smoke warning first (visible tell before the hole opens)
                Location warn = new Location(p.getWorld(), hx, surfaceY + 1, hz);
                p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, warn,
                        6, 0.3, 0.4, 0.3, 0.04);

                // Dig 2 blocks down on collapsible surface
                for (int dy = 0; dy >= -2; dy--) {
                    Block b = p.getWorld().getBlockAt((int) hx, surfaceY + dy, (int) hz);
                    if (COLLAPSIBLE.contains(b.getType())) {
                        p.getWorld().spawnParticle(Particle.BLOCK,
                                b.getLocation().clone().add(0.5, 0.5, 0.5),
                                8, 0.3, 0.3, 0.3, 0.05, b.getBlockData());
                        b.setType(Material.AIR);
                    }
                }
                // Ground crack sound
                p.playSound(warn, Sound.BLOCK_GRAVEL_BREAK, SoundCategory.BLOCKS, 1.0f, 0.6f);
            }
        }
    }

    private void groundCrackParticles() {
        for (int i = 0; i < 8; i++) {
            double r = 5 + rng.nextDouble() * 50;
            double a = rng.nextDouble() * Math.PI * 2;
            double x = volcano.getX() + Math.cos(a) * r;
            double z = volcano.getZ() + Math.sin(a) * r;
            int y = volcano.getWorld().getHighestBlockYAt((int) x, (int) z);
            Location l = new Location(volcano.getWorld(), x, y + 0.1, z);
            volcano.getWorld().spawnParticle(Particle.BLOCK, l,
                    4, 0.5, 0.2, 0.5, 0.04, Material.STONE.createBlockData());
            volcano.getWorld().spawnParticle(Particle.SMOKE, l,
                    2, 0.3, 0.3, 0.3, 0.02);
        }
    }

    /** Breaks weak overhead blocks to stop players sealing themselves in completely. */
    private void collapseWeakRoofs() {
        Set<Material> roofMats = Set.of(
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.DIRT, Material.GRAVEL, Material.OAK_LEAVES, Material.SPRUCE_LEAVES
        );
        for (Player p : nearby(500)) {
            Location base = p.getLocation();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = 1; dy <= 3; dy++) {
                        Block b = base.getWorld().getBlockAt(
                                base.getBlockX() + dx,
                                base.getBlockY() + dy,
                                base.getBlockZ() + dz);
                        if (roofMats.contains(b.getType()) && rng.nextInt(6) == 0) {
                            base.getWorld().spawnParticle(Particle.BLOCK,
                                    b.getLocation().clone().add(0.5, 0.5, 0.5),
                                    6, 0.3, 0.3, 0.3, 0.04, b.getBlockData());
                            b.setType(Material.AIR);
                        }
                    }
                }
            }
        }
    }
}
