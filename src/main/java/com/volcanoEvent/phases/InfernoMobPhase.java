package com.volcanoEvent.phases;

import com.volcanoEvent.VolcanoEventPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class InfernoMobPhase extends PhaseBase implements Listener {

    private static final double RADIUS = 900;
    private static final String TAG = "inferno_mob";
    private final Random rng = new Random();
    private final Set<Entity> mobs = Collections.newSetFromMap(new WeakHashMap<>());
    private int totalSpawned = 0;
    private static final int MAX_MOBS = 60;

    // Wave sizes per call
    private static final int INITIAL_WAVE = 10;
    private static final int PERIODIC_WAVE = 6;

    public InfernoMobPhase(VolcanoEventPlugin plugin, Location volcano) {
        super(plugin, volcano);
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        title("§4👹 INFERNO ASSAULT", "§cDemon creatures emerge from the volcano!");
        broadcast("§4§l[Volcano] PHASE 5 — INFERNO ASSAULT! Creatures of flame pour from the caldera!");

        // Initial burst
        later(10, () -> wave(INITIAL_WAVE));

        // Periodic wave every 30s
        every(20*30, 20*30, () -> wave(PERIODIC_WAVE));

        // Force target every 2s — they aggro without being hit
        every(0, 40, this::forceAggro);

        // Mob fire aura particles — one pass per mob, cheap
        every(0, 6, this::mobAura);

        // Continued fire rain
        every(0, 10, () -> {
            for (Player p : nearby(700)) {
                if (rng.nextInt(3) != 0) continue;
                double ox = (rng.nextDouble()-0.5)*14;
                double oz = (rng.nextDouble()-0.5)*14;
                Location drop = p.getLocation().clone().add(ox, 9 + rng.nextDouble()*4, oz);
                p.getWorld().spawnParticle(Particle.FLAME, drop, 1, 0, -0.12, 0, 0.03);
            }
        });

        // Ambient sounds
        every(0, 35, () -> {
            for (Player p : nearby(RADIUS))
                p.playSound(volcano, Sound.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE,
                        0.6f, 0.5f + rng.nextFloat()*0.3f);
        });

        later(20*30,  () -> broadcast("§4[Volcano] The inferno creatures are relentless — fight or die!"));
        later(20*120, () -> broadcast("§4[Volcano] They keep coming! Aim for the glowing ones first!"));
        later(20*300, () -> {
            broadcast("§4§l[Volcano] FINAL PUSH — the volcano is preparing one last wave of creatures!");
            wave(12);
        });
        later(20*500, () -> broadcast("§c[Volcano] The last of the inferno horde has emerged. Finish them!"));
    }

    private void wave(int count) {
        int toSpawn = Math.min(count, MAX_MOBS - totalSpawned);
        for (int i = 0; i < toSpawn; i++) spawnMob();
    }

    private void spawnMob() {
        double a = rng.nextDouble() * Math.PI * 2;
        double r = 2 + rng.nextDouble() * 10;
        Location loc = volcano.clone().add(Math.cos(a)*r, 0, Math.sin(a)*r);

        // Walk down to solid ground
        for (int i = 0; i < 10; i++) {
            if (loc.getBlock().getType() != Material.AIR) break;
            loc.subtract(0, 1, 0);
        }
        loc.add(0, 1, 0);

        // Pick type
        EntityType type = switch (rng.nextInt(10)) {
            case 0, 1, 2, 3 -> EntityType.PIGLIN;
            case 4, 5, 6    -> EntityType.ZOMBIFIED_PIGLIN;
            default          -> EntityType.BLAZE;
        };

        LivingEntity mob = (LivingEntity) volcano.getWorld().spawnEntity(loc, type);
        if (mob == null) return;

        applyModifiers(mob);
        mob.setMetadata(TAG, new FixedMetadataValue(plugin, true));
        mobs.add(mob);
        totalSpawned++;

        // Spawn effect
        loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0,1,0), 20, 0.8, 1, 0.8, 0.08);
        loc.getWorld().spawnParticle(Particle.LAVA,  loc.clone().add(0,1,0),  8, 0.4, 0.5, 0.4, 0);
    }

    private void applyModifiers(LivingEntity mob) {
        // Double max health
        attr(mob, Attribute.MAX_HEALTH, v -> v * 2);
        mob.setHealth(Objects.requireNonNull(mob.getAttribute(Attribute.MAX_HEALTH)).getValue());

        // Double movement speed
        attr(mob, Attribute.MOVEMENT_SPEED, v -> v * 2.0);

        // Double attack damage
        attr(mob, Attribute.ATTACK_DAMAGE, v -> v * 2.0);

        // Big follow range — 80 blocks
        attr(mob, Attribute.FOLLOW_RANGE, v -> 80.0);

        // Permanent fire
        mob.setFireTicks(Integer.MAX_VALUE);

        // Potion buffs
        mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,         Integer.MAX_VALUE, 1, false, false));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,  Integer.MAX_VALUE, 0, false, false));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,            Integer.MAX_VALUE, 1, false, false));

        // Name tag
        mob.setCustomName("§4§l☄ Inferno " + label(mob.getType()));
        mob.setCustomNameVisible(true);

        // Piglin-specific: no zombification, hunting on
        if (mob instanceof Piglin p) {
            p.setImmuneToZombification(true);
            p.setIsAbleToHunt(true);
        }
        if (mob instanceof ZombifiedPiglin zp) {
            zp.setAnger(Integer.MAX_VALUE);
        }
    }

    private void attr(LivingEntity mob, Attribute attr, java.util.function.DoubleUnaryOperator fn) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) inst.setBaseValue(fn.applyAsDouble(inst.getBaseValue()));
    }

    private String label(EntityType t) {
        return switch (t) {
            case PIGLIN           -> "Piglin";
            case ZOMBIFIED_PIGLIN -> "Brute";
            case BLAZE            -> "Blaze";
            default               -> t.name();
        };
    }

    private void forceAggro() {
        for (Entity e : new ArrayList<>(mobs)) {
            if (!e.isValid() || !(e instanceof Mob mob)) continue;
            Player nearest = null;
            double bestDist = Double.MAX_VALUE;
            for (Player p : nearby(500)) {
                double d = p.getLocation().distanceSquared(mob.getLocation());
                if (d < bestDist) { bestDist = d; nearest = p; }
            }
            if (nearest != null) mob.setTarget(nearest);
        }
    }

    private void mobAura() {
        for (Entity e : mobs) {
            if (!e.isValid()) continue;
            Location l = e.getLocation().add(0, 1, 0);
            l.getWorld().spawnParticle(Particle.FLAME, l, 3, 0.35, 0.4, 0.35, 0.02);
            l.getWorld().spawnParticle(Particle.SMOKE, l.clone().add(0,0.5,0), 1, 0.2, 0.2, 0.2, 0.01);
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent ev) {
        if (!ev.getEntity().hasMetadata(TAG)) return;
        Location loc = ev.getEntity().getLocation();
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 15, 0.8, 0.8, 0.8, 0.08);

        // Loot drop
        ev.getDrops().clear();
        ev.setDroppedExp(0);
        if (rng.nextInt(2) == 0)
            ev.getDrops().add(new org.bukkit.inventory.ItemStack(Material.GOLD_INGOT, 1 + rng.nextInt(4)));
        if (rng.nextInt(4) == 0)
            ev.getDrops().add(new org.bukkit.inventory.ItemStack(Material.BLAZE_ROD, 1 + rng.nextInt(2)));
        if (rng.nextInt(6) == 0)
            ev.getDrops().add(new org.bukkit.inventory.ItemStack(Material.NETHERITE_SCRAP, 1));
        if (rng.nextInt(10) == 0)
            ev.getDrops().add(new org.bukkit.inventory.ItemStack(Material.ANCIENT_DEBRIS, 1));

        mobs.remove(ev.getEntity());
    }

    @Override
    protected void onStop() {
        HandlerList.unregisterAll(this);
        for (Entity e : mobs) {
            if (e.isValid()) {
                e.getWorld().spawnParticle(Particle.EXPLOSION, e.getLocation(), 1);
                e.remove();
            }
        }
        mobs.clear();
    }
}
