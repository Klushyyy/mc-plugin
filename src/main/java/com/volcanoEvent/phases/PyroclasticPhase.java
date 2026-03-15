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
        title("§4💥 PYROCLASTIC SURGE", "§cBlazing debris rains from above!");
        broadcast("§4§l[Volcano] PHASE 4 — PYROCLASTIC SURGE! Debris is launching from the volcano!");

        // Debris volleys — throttled for low-end: 3 blocks every 20 ticks
        every(0, 20, () -> {
            for (int i = 0; i < 3; i++) launchDebris();
        });

        // Lava geyser still going (continued from eruption)
        every(0, 5, () -> {
            for (int i = 0; i < 8; i++) {
                double ox = (rng.nextDouble()-0.5)*5;
                double oz = (rng.nextDouble()-0.5)*5;
                Location l = volcano.clone().add(ox, 0.5, oz);
                volcano.getWorld().spawnParticle(Particle.LAVA, l, 1,
                        (rng.nextDouble()-0.5)*0.1, 0.3, (rng.nextDouble()-0.5)*0.1, 0);
                volcano.getWorld().spawnParticle(Particle.FLAME, l, 1,
                        0, 0.35, 0, 0.05);
            }
        });

        // Ash blackout — heavy ash near volcano
        every(0, 15, () -> {
            for (Player p : nearby(RADIUS)) {
                double dist = p.getLocation().distance(volcano);
                if (dist < 250) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, false, false, false));
                } else if (dist < 550) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, false, false, false));
                }
                // Continued heat damage but lighter than phase 3
                if (dist < 150 && rng.nextInt(3)==0) {
                    p.damage(1.5);
                    p.setFireTicks(60);
                }
            }
        });

        // Shockwave ring every 8 seconds
        every(0, 20 * 8, this::shockwave);

        // Debris impact check
        every(5, 15, this::checkLanded);

        // Sounds
        every(0, 20, () -> {
            for (Player p : nearby(RADIUS)) {
                p.playSound(volcano, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.AMBIENT,
                        0.8f, 0.5f + rng.nextFloat() * 0.4f);
                p.playSound(volcano, Sound.BLOCK_GRAVEL_BREAK, SoundCategory.AMBIENT, 1.2f, 0.7f);
            }
        });

        // Fire rain continues
        every(0, 8, () -> {
            for (Player p : nearby(600)) {
                double dist = p.getLocation().distance(volcano);
                int cnt = dist < 200 ? 5 : (dist < 400 ? 3 : 1);
                for (int i = 0; i < cnt; i++) {
                    double ox = (rng.nextDouble()-0.5)*16;
                    double oz = (rng.nextDouble()-0.5)*16;
                    Location drop = p.getLocation().clone().add(ox, 10 + rng.nextDouble()*5, oz);
                    p.getWorld().spawnParticle(Particle.FLAME, drop, 1, 0, -0.15, 0, 0.04);
                }
            }
        });

        later(20*60,  () -> broadcast("§4[Volcano] Massive boulders are being hurled across the island!"));
        later(20*240, () -> broadcast("§4[Volcano] The pyroclastic surge is reaching everything — nowhere is truly safe!"));
        later(20*480, () -> broadcast("§c[Volcano] The bombardment begins to ease... but something worse stirs in the smoke."));
    }

    private void launchDebris() {
        // Pick target in a wide range, bias toward players
        List<Player> players = nearby(700);
        Location target;
        if (!players.isEmpty() && rng.nextInt(3) != 0) {
            Player t = players.get(rng.nextInt(players.size()));
            target = t.getLocation().clone().add(
                    (rng.nextDouble()-0.5)*20,
                    0,
                    (rng.nextDouble()-0.5)*20);
        } else {
            double angle = rng.nextDouble() * Math.PI * 2;
            double dist  = 30 + rng.nextDouble() * 500;
            target = volcano.clone().add(Math.cos(angle)*dist, 0, Math.sin(angle)*dist);
        }

        Location launch = volcano.clone().add(
                (rng.nextDouble()-0.5)*5,
                1 + rng.nextDouble()*3,
                (rng.nextDouble()-0.5)*5);

        Vector toTarget = target.toVector().subtract(launch.toVector());
        double hDist = Math.sqrt(toTarget.getX()*toTarget.getX() + toTarget.getZ()*toTarget.getZ());
        double flightT = Math.max(15, hDist / 2.8);
        double vy = Math.min(1.6, (toTarget.getY() + 0.04*flightT*flightT/2) / flightT);
        double vx = Math.max(-0.7, Math.min(0.7, toTarget.getX() / flightT));
        double vz = Math.max(-0.7, Math.min(0.7, toTarget.getZ() / flightT));

        Material mat = DEBRIS_MATS[rng.nextInt(DEBRIS_MATS.length)];
        FallingBlock fb = launch.getWorld().spawnFallingBlock(launch, mat.createBlockData());
        fb.setVelocity(new Vector(vx, vy, vz));
        fb.setDropItem(false);
        fb.setHurtEntities(true, 8.0f, 10);
        fb.setMetadata(TAG, new FixedMetadataValue(plugin, true));
        debris.add(fb);

        // Trail — runs per-entity, very lightweight (1 particle/tick)
        int[] t = {0};
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!fb.isValid() || t[0]++ > 100) { task.cancel(); return; }
            fb.getWorld().spawnParticle(Particle.FLAME, fb.getLocation(), 1, 0.05, 0.05, 0.05, 0.01);
            fb.getWorld().spawnParticle(Particle.SMOKE, fb.getLocation(), 1, 0.08, 0.08, 0.08, 0.01);
        }, 0, 1);
    }

    private void checkLanded() {
        Iterator<Entity> it = debris.iterator();
        while (it.hasNext()) {
            Entity e = it.next();
            if (!e.isValid()) {
                Location loc = e.getLocation();
                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
                loc.getWorld().spawnParticle(Particle.LAVA, loc, 6, 0.8, 0.3, 0.8, 0);
                loc.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0,0.5,0), 8, 1, 0.5, 1, 0.04);
                for (Player p : nearby(180)) {
                    if (p.getLocation().distanceSquared(loc) < 200*200)
                        p.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.6f, 0.8f + rng.nextFloat()*0.3f);
                }
                it.remove();
            }
        }
    }

    private void shockwave() {
        // Knockback ring expanding from volcano
        for (Player p : nearby(350)) {
            double dist = p.getLocation().distance(volcano);
            Vector dir = p.getLocation().toVector().subtract(volcano.toVector()).normalize();
            double force = dist < 100 ? 0.7 : 0.35;
            dir.setY(0.3).normalize().multiply(force);
            if (!p.isFlying()) p.setVelocity(p.getVelocity().add(dir));
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.5f, 0.5f);
        }
        // Visual ring
        int pts = 48;
        for (double r = 10; r <= 120; r += 25) {
            for (int i = 0; i < pts; i++) {
                double a = (Math.PI*2/pts)*i;
                double x = volcano.getX() + Math.cos(a)*r;
                double z = volcano.getZ() + Math.sin(a)*r;
                int y = volcano.getWorld().getHighestBlockYAt((int)x, (int)z);
                volcano.getWorld().spawnParticle(Particle.FLAME,
                        new Location(volcano.getWorld(), x, y+0.3, z), 1, 0, 0.2, 0, 0);
            }
        }
    }

    @EventHandler
    public void onDebrisHit(EntityDamageByEntityEvent ev) {
        if (!(ev.getDamager() instanceof FallingBlock fb)) return;
        if (!fb.hasMetadata(TAG)) return;
        if (!(ev.getEntity() instanceof Player p)) return;
        ev.setDamage(ev.getDamage() * 1.6);
        p.sendTitle("§c§l💥 DEBRIS HIT", "§7" + String.format("%.1f", ev.getFinalDamage()) + " damage!", 3, 20, 8);
        p.setFireTicks(80);
    }

    @Override
    protected void onStop() {
        HandlerList.unregisterAll(this);
        for (Entity e : debris) if (e.isValid()) e.remove();
        debris.clear();
    }
}
