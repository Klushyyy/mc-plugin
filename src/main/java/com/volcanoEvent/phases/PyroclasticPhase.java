package com.volcanoEvent.phases;

import com.volcanoEvent.VolcanoEventPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

public class PyroclasticPhase extends PhaseBase implements Listener {

    private static final double RADIUS = 900;
    private static final String TAG = "pyro_debris";
    private final Random rng = new Random();
    private final Set<Entity> debris = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Material[] DEBRIS_MATS = {
        Material.NETHERRACK, Material.MAGMA_BLOCK, Material.BASALT,
        Material.BLACKSTONE, Material.COBBLESTONE, Material.GRAVEL,
        Material.STONE, Material.TUFF, Material.OBSIDIAN
    };

    public PyroclasticPhase(VolcanoEventPlugin plugin, Location volcano) {
        super(plugin, volcano);
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        title("§4Phase 4", "§fPyroclastic Surge");
        broadcast("§4[VOLCANO] Phase 4: Pyroclastic Surge. Debris incoming.");

        // Heavy barrage — 8 blocks every 8 ticks (~20/sec at peak)
        every(0, 8, () -> {
            for (int i = 0; i < 8; i++) launchDebris();
        });

        // Extra-heavy volley burst every 15 seconds
        every(20 * 15, 20 * 15, () -> {
            broadcast("§4[VOLCANO] Major debris volley!");
            for (int i = 0; i < 30; i++) launchDebris();
        });

        // Shockwave knockback every 10 seconds
        every(0, 20 * 10, this::shockwave);

        // Lava geyser continues from eruption phase
        every(0, 5, () -> {
            for (int i = 0; i < 6; i++) {
                double ox = (rng.nextDouble() - 0.5) * 5;
                double oz = (rng.nextDouble() - 0.5) * 5;
                Location l = volcano.clone().add(ox, 0.5, oz);
                volcano.getWorld().spawnParticle(Particle.LAVA, l, 1,
                        (rng.nextDouble()-0.5)*0.1, 0.35, (rng.nextDouble()-0.5)*0.1, 0);
                volcano.getWorld().spawnParticle(Particle.FLAME, l, 1, 0, 0.3, 0, 0.04);
            }
        });

        // Dense ash cloud (grey concrete powder) — no blindness
        every(0, 10, () -> {
            for (Player p : nearby(RADIUS)) {
                double dist = p.getLocation().distance(volcano);
                // Heavy ash slowness within 300 blocks — no damage
                if (dist < 300) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            30, 1, false, false, false));
                } else if (dist < 600) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                            25, 0, false, false, false));
                }
                // Dense ash cloud around player
                int ashCount = dist < 200 ? 4 : (dist < 400 ? 2 : 1);
                for (int i = 0; i < ashCount; i++) {
                    double ox = (rng.nextDouble()-0.5)*8;
                    double oz = (rng.nextDouble()-0.5)*8;
                    Location al = p.getLocation().clone().add(ox, 1 + rng.nextDouble()*3, oz);
                    p.getWorld().spawnParticle(Particle.BLOCK, al,
                            2, 0.4, 0.4, 0.4, 0.01,
                            Material.GRAY_CONCRETE_POWDER.createBlockData());
                }
            }
        });

        // Check for landed debris
        every(5, 12, this::checkLanded);

        // Sounds
        every(0, 18, () -> {
            for (Player p : nearby(RADIUS)) {
                p.playSound(volcano, Sound.ENTITY_GENERIC_EXPLODE,
                        SoundCategory.AMBIENT, 0.7f, 0.5f + rng.nextFloat() * 0.4f);
            }
        });

        later(20 * 30,  () -> broadcast("§4[VOLCANO] Boulders raining across the island."));
        later(20 * 300, () -> broadcast("§4[VOLCANO] Pyroclastic surge at maximum intensity."));
        later(20 * 480, () -> broadcast("§4[VOLCANO] Debris storm subsiding."));
    }

    private void launchDebris() {
        // Bias toward players 60% of the time
        List<Player> players = nearby(700);
        Location target;
        if (!players.isEmpty() && rng.nextInt(5) < 3) {
            Player t = players.get(rng.nextInt(players.size()));
            target = t.getLocation().clone().add(
                    (rng.nextDouble()-0.5)*25,
                    0,
                    (rng.nextDouble()-0.5)*25);
        } else {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist  = 20 + rng.nextDouble() * 600;
            target = volcano.clone().add(Math.cos(angle)*dist, 0, Math.sin(angle)*dist);
        }

        Location launch = volcano.clone().add(
                (rng.nextDouble()-0.5)*4, 1 + rng.nextDouble()*2, (rng.nextDouble()-0.5)*4);

        Vector toTarget = target.toVector().subtract(launch.toVector());
        double hDist = Math.sqrt(toTarget.getX()*toTarget.getX() + toTarget.getZ()*toTarget.getZ());
        double flightT = Math.max(12, hDist / 3.0);
        double vy = Math.min(1.7, (toTarget.getY() + 0.04*flightT*flightT/2) / flightT);
        double vx = Math.max(-0.8, Math.min(0.8, toTarget.getX() / flightT));
        double vz = Math.max(-0.8, Math.min(0.8, toTarget.getZ() / flightT));

        Material mat = DEBRIS_MATS[rng.nextInt(DEBRIS_MATS.length)];
        FallingBlock fb = launch.getWorld().spawnFallingBlock(launch, mat.createBlockData());
        fb.setVelocity(new Vector(vx, vy, vz));
        fb.setDropItem(false);
        fb.setHurtEntities(true);
        fb.setMetadata(TAG, new FixedMetadataValue(plugin, true));
        debris.add(fb);

        // Lightweight trail
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!fb.isValid()) { task.cancel(); return; }
            fb.getWorld().spawnParticle(Particle.FLAME, fb.getLocation(), 1, 0.05, 0.05, 0.05, 0.01);
        }, 0, 2);
    }

    private void checkLanded() {
        Iterator<Entity> it = debris.iterator();
        while (it.hasNext()) {
            Entity e = it.next();
            if (!e.isValid()) {
                Location loc = e.getLocation();
                // Small explosion: power 1.8, fire=true, no block breaks
                loc.getWorld().createExplosion(loc, 1.8f, true, false);
                // Ash burst
                loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.5, 0),
                        12, 1, 0.5, 1, 0.05,
                        Material.GRAY_CONCRETE_POWDER.createBlockData());
                loc.getWorld().spawnParticle(Particle.LAVA, loc, 5, 0.8, 0.3, 0.8, 0);
                it.remove();
            }
        }
    }

    private void shockwave() {
        for (Player p : nearby(400)) {
            double dist = p.getLocation().distance(volcano);
            Vector dir = p.getLocation().toVector().subtract(volcano.toVector()).normalize();
            double force = dist < 100 ? 0.8 : 0.4;
            dir.setY(0.35).normalize().multiply(force);
            if (!p.isFlying()) p.setVelocity(p.getVelocity().add(dir));
        }
        // Visual ring
        for (int i = 0; i < 48; i++) {
            double a = (Math.PI * 2 / 48) * i;
            for (double r = 15; r <= 100; r += 30) {
                double x = volcano.getX() + Math.cos(a) * r;
                double z = volcano.getZ() + Math.sin(a) * r;
                int y = volcano.getWorld().getHighestBlockYAt((int) x, (int) z);
                volcano.getWorld().spawnParticle(Particle.FLAME,
                        new Location(volcano.getWorld(), x, y + 0.3, z),
                        1, 0, 0.2, 0, 0);
            }
        }
    }

    // Boost damage on direct debris hit + set fire
    @EventHandler
    public void onDebrisHit(EntityDamageByEntityEvent ev) {
        if (!(ev.getDamager() instanceof FallingBlock fb)) return;
        if (!fb.hasMetadata(TAG)) return;
        if (!(ev.getEntity() instanceof Player p)) return;
        ev.setDamage(ev.getDamage() * 1.8);
        p.setFireTicks(100);
        p.sendTitle("§c§lDEBRIS HIT",
                "§7" + String.format("%.1f", ev.getFinalDamage()) + " damage",
                3, 18, 8);
    }

    @Override
    protected void onStop() {
        HandlerList.unregisterAll(this);
        for (Entity e : debris) if (e.isValid()) e.remove();
        debris.clear();
    }
}
