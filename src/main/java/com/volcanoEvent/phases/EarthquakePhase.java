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
    // blocks we crack open to stop players hiding — restored on stop
    private final List<Location> crackedBlocks = new ArrayList<>();

    public EarthquakePhase(VolcanoEventPlugin plugin, Location volcano) {
        super(plugin, volcano);
    }

    @Override
    public void start() {
        title("§e⚡ EARTHQUAKE", "§7The ground tears itself apart!");
        broadcast("§e§l[Volcano] PHASE 2 — EARTHQUAKE! Brace yourself!");

        // Core shake tick every 5 ticks
        every(0, 5, () -> {
            tick++;
            boolean intense = tick > 20 * 18; // intensify in final 24 seconds

            for (Player p : nearby(RADIUS)) {
                shake(p, intense);
            }
        });

        // Periodic throw — launches players into the air
        every(0, 30, () -> {
            for (Player p : nearby(RADIUS)) {
                double dist = p.getLocation().distance(volcano);
                // closer = bigger launch
                double force = dist < 200 ? 0.55 : (dist < 500 ? 0.35 : 0.18);
                if (!p.isFlying()) {
                    Vector v = p.getVelocity();
                    v.setY(v.getY() + force);
                    p.setVelocity(v);
                }
            }
        });

        // Deep boom sound every 2s
        every(0, 40, () -> {
            for (Player p : nearby(RADIUS)) {
                float pitch = 0.3f + rng.nextFloat() * 0.3f;
                p.playSound(volcano, Sound.ENTITY_RAVAGER_ROAR, SoundCategory.AMBIENT, 1.8f, pitch);
                p.playSound(volcano, Sound.BLOCK_GRAVEL_BREAK, SoundCategory.AMBIENT, 1.5f, 0.5f);
            }
        });

        // Crack particles along ground
        every(0, 10, this::groundCracks);

        // Structural damage — collapses any player-built dirt/wood roofs to prevent boxing in
        // Only targets common "shelter" block types in a small radius per player
        every(20, 80, this::collapseWeakStructures);

        // Warden sonic boom for deep rumble
        every(0, 60, () -> {
            for (Player p : nearby(RADIUS))
                p.playSound(volcano, Sound.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.AMBIENT, 0.3f, 0.3f);
        });

        // Messages
        later(20 * 20, () -> broadcast("§e[Volcano] The island is tearing apart — you can't stay standing!"));
        later(20 * 60, () -> broadcast("§c[Volcano] Massive tremor incoming!"));
        later(20 * 90, () -> broadcast("§c[Volcano] The earthquake reaches its peak! The eruption follows!"));
    }

    private void shake(Player p, boolean intense) {
        double shakeX = (rng.nextDouble() - 0.5) * (intense ? 0.28 : 0.15);
        double shakeZ = (rng.nextDouble() - 0.5) * (intense ? 0.28 : 0.15);
        p.setVelocity(p.getVelocity().add(new Vector(shakeX, 0, shakeZ)));
        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, false));
        if (intense) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 1, false, false, false));
            if (rng.nextInt(12) == 0)
                p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20, 0, false, false, false));
        }
    }

    private void groundCracks() {
        int count = 12;
        for (int i = 0; i < count; i++) {
            double r = 8 + rng.nextDouble() * 60;
            double a = rng.nextDouble() * Math.PI * 2;
            double x = volcano.getX() + Math.cos(a) * r;
            double z = volcano.getZ() + Math.sin(a) * r;
            int y = volcano.getWorld().getHighestBlockYAt((int)x, (int)z);
            Location l = new Location(volcano.getWorld(), x, y + 0.1, z);
            volcano.getWorld().spawnParticle(Particle.BLOCK, l, 5, 0.5, 0.2, 0.5, 0.04,
                    Material.STONE.createBlockData());
            volcano.getWorld().spawnParticle(Particle.SMOKE, l, 2, 0.3, 0.4, 0.3, 0.02);
            volcano.getWorld().spawnParticle(Particle.DUST, l, 3, 0.3, 0.2, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(70, 50, 40), 1.2f));
        }
    }

    /**
     * Breaks weak overhead blocks (logs, planks, dirt) near players — prevents
     * them from sealing themselves inside a box completely.  Only natural-ish materials.
     */
    private void collapseWeakStructures() {
        Set<Material> weak = Set.of(
                Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
                Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
                Material.DIRT, Material.GRAVEL, Material.SAND,
                Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES,
                Material.COBBLESTONE, Material.STONE_BRICKS
        );

        for (Player p : nearby(400)) {
            // Scan a 5-block cube above and around the player
            Location base = p.getLocation();
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = 1; dy <= 4; dy++) {
                        Block b = base.getWorld().getBlockAt(
                                base.getBlockX() + dx,
                                base.getBlockY() + dy,
                                base.getBlockZ() + dz);
                        if (weak.contains(b.getType()) && rng.nextInt(5) == 0) {
                            Location bl = b.getLocation();
                            volcano.getWorld().spawnParticle(Particle.BLOCK, bl.clone().add(0.5, 0.5, 0.5),
                                    8, 0.3, 0.3, 0.3, 0.05, b.getBlockData());
                            b.setType(Material.AIR);
                            crackedBlocks.add(bl);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void onStop() {
        // We intentionally do NOT restore broken blocks — the island stays damaged.
        crackedBlocks.clear();
    }
}
