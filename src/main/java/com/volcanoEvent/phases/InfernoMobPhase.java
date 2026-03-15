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
import java.util.function.DoubleUnaryOperator;

public class InfernoMobPhase extends PhaseBase implements Listener {

    // Spawn mobs in the full 500-block radius around volcano
    private static final double SPAWN_RADIUS = 500;
    private static final double AGGRO_RADIUS = 500;
    private static final String TAG = "inferno_mob";
    private final Random rng = new Random();
    private final Set<Entity> mobs = Collections.newSetFromMap(new WeakHashMap<>());
    private int totalSpawned = 0;
    private static final int MAX_MOBS = 100;

    // Materials considered safe to spawn on (not lava/water/air)
    private static final Set<Material> SAFE_SURFACE = new HashSet<>(Arrays.asList(
        Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
        Material.STONE, Material.COBBLESTONE, Material.GRAVEL, Material.SAND,
        Material.NETHERRACK, Material.BASALT, Material.BLACKSTONE,
        Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.BIRCH_PLANKS,
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.MOSSY_COBBLESTONE,
        Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.TUFF,
        Material.MAGMA_BLOCK
    ));

    public InfernoMobPhase(VolcanoEventPlugin plugin, Location volcano) {
        super(plugin, volcano);
    }

    @Override
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        title("§4Phase 5", "§fInferno Assault");
        broadcast("§4[VOLCANO] Phase 5: Inferno Assault. Creatures emerging from the volcano.");

        // Initial burst across the island
        later(10, () -> wave(15));

        // Waves every 25 seconds
        every(20 * 25, 20 * 25, () -> wave(8));

        // Force aggro every 2 seconds
        every(0, 40, this::forceAggro);

        // Mob fire aura
        every(0, 5, this::mobAura);

        // Fire rain (visual only)
        every(0, 8, () -> {
            for (Player p : nearby(800)) {
                if (rng.nextInt(4) != 0) continue;
                double ox = (rng.nextDouble()-0.5)*14;
                double oz = (rng.nextDouble()-0.5)*14;
                Location drop = p.getLocation().clone().add(ox, 9 + rng.nextDouble()*4, oz);
                p.getWorld().spawnParticle(Particle.FLAME, drop, 1, 0, -0.1, 0, 0.03);
            }
        });

        // Sounds
        every(0, 30, () -> {
            for (Player p : nearby(900))
                p.playSound(volcano, Sound.ENTITY_BLAZE_AMBIENT, SoundCategory.HOSTILE,
                        0.5f, 0.5f + rng.nextFloat() * 0.3f);
        });

        later(20 * 25,  () -> broadcast("§4[VOLCANO] Inferno mobs spreading across the island."));
        later(20 * 120, () -> broadcast("§4[VOLCANO] Multiple waves active. Coordinate."));
        later(20 * 300, () -> {
            broadcast("§4[VOLCANO] Final wave.");
            wave(20);
        });
    }

    private void wave(int count) {
        int toSpawn = Math.min(count, MAX_MOBS - totalSpawned);
        for (int i = 0; i < toSpawn; i++) spawnMob();
    }

    private void spawnMob() {
        // Spawn anywhere within 500 blocks — find a safe surface block
        Location spawnLoc = findSafeSpawn();
        if (spawnLoc == null) return;

        EntityType type = switch (rng.nextInt(10)) {
            case 0, 1, 2, 3 -> EntityType.PIGLIN;
            case 4, 5, 6    -> EntityType.ZOMBIFIED_PIGLIN;
            default          -> EntityType.BLAZE;
        };

        LivingEntity mob = (LivingEntity) spawnLoc.getWorld().spawnEntity(spawnLoc, type);
        if (mob == null) return;

        applyModifiers(mob);
        mob.setMetadata(TAG, new FixedMetadataValue(plugin, true));
        mobs.add(mob);
        totalSpawned++;

        // Spawn FX
        spawnLoc.getWorld().spawnParticle(Particle.FLAME,
                spawnLoc.clone().add(0, 1, 0), 15, 0.7, 1, 0.7, 0.07);
        spawnLoc.getWorld().spawnParticle(Particle.LAVA,
                spawnLoc.clone().add(0, 1, 0),  6, 0.4, 0.5, 0.4, 0);
    }

    /**
     * Finds a random spawn location within 500 blocks of the volcano.
     * Checks that the surface block is safe (not lava, water, or air).
     * Returns null if no safe location found after attempts.
     */
    private Location findSafeSpawn() {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle  = rng.nextDouble() * Math.PI * 2;
            double dist   = 10 + rng.nextDouble() * SPAWN_RADIUS;
            double x = volcano.getX() + Math.cos(angle) * dist;
            double z = volcano.getZ() + Math.sin(angle) * dist;

            // Check chunk is loaded before getting highest block
            if (!volcano.getWorld().isChunkLoaded((int)x >> 4, (int)z >> 4)) continue;

            int surfaceY = volcano.getWorld().getHighestBlockYAt((int)x, (int)z);
            Material surfaceMat = volcano.getWorld()
                    .getBlockAt((int)x, surfaceY, (int)z).getType();

            // Must be safe surface and not lava/water
            if (!SAFE_SURFACE.contains(surfaceMat)) continue;

            Location loc = new Location(volcano.getWorld(), x, surfaceY + 1.0, z);
            // Final check: spawn block is air
            if (loc.getBlock().getType() != Material.AIR) continue;

            return loc;
        }
        return null; // no safe spot found
    }

    private void applyModifiers(LivingEntity mob) {
        // Paper 1.21.1 uses GENERIC_ prefix
        modAttr(mob, Attribute.GENERIC_MAX_HEALTH,     v -> v * 2.0);
        AttributeInstance hp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) mob.setHealth(hp.getValue());

        modAttr(mob, Attribute.GENERIC_MOVEMENT_SPEED, v -> v * 2.0);
        modAttr(mob, Attribute.GENERIC_ATTACK_DAMAGE,  v -> v * 2.0);
        modAttr(mob, Attribute.GENERIC_FOLLOW_RANGE,   v -> 80.0);

        mob.setFireTicks(Integer.MAX_VALUE);
        mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                Integer.MAX_VALUE, 1, false, false));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE,
                Integer.MAX_VALUE, 0, false, false));
        mob.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                Integer.MAX_VALUE, 1, false, false));

        mob.setCustomName("§4§l☄ Inferno " + mobLabel(mob.getType()));
        mob.setCustomNameVisible(true);

        if (mob instanceof Piglin p) {
            p.setImmuneToZombification(true);
            p.setIsAbleToHunt(true);
        }
        // Zombified piglins are PigZombie in the cast API
        if (mob instanceof PigZombie pz) {
            pz.setAngry(true);
            pz.setAnger(Integer.MAX_VALUE);
        }
    }

    private void modAttr(LivingEntity mob, Attribute attr, DoubleUnaryOperator fn) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) inst.setBaseValue(fn.applyAsDouble(inst.getBaseValue()));
    }

    private String mobLabel(EntityType t) {
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
            double best = Double.MAX_VALUE;
            for (Player p : nearby(AGGRO_RADIUS)) {
                double d = p.getLocation().distanceSquared(mob.getLocation());
                if (d < best) { best = d; nearest = p; }
            }
            if (nearest != null) mob.setTarget(nearest);
        }
    }

    private void mobAura() {
        for (Entity e : mobs) {
            if (!e.isValid()) continue;
            Location l = e.getLocation().add(0, 1, 0);
            l.getWorld().spawnParticle(Particle.FLAME, l, 2, 0.3, 0.4, 0.3, 0.02);
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent ev) {
        if (!ev.getEntity().hasMetadata(TAG)) return;
        Location loc = ev.getEntity().getLocation();
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 12, 0.8, 0.8, 0.8, 0.07);

        ev.getDrops().clear();
        ev.setDroppedExp(0);
        if (rng.nextInt(2) == 0)
            ev.getDrops().add(new org.bukkit.inventory.ItemStack(Material.GOLD_INGOT,
                    1 + rng.nextInt(4)));
        if (rng.nextInt(4) == 0)
            ev.getDrops().add(new org.bukkit.inventory.ItemStack(Material.BLAZE_ROD,
                    1 + rng.nextInt(2)));
        if (rng.nextInt(6) == 0)
            ev.getDrops().add(new org.bukkit.inventory.ItemStack(Material.NETHERITE_SCRAP, 1));
        if (rng.nextInt(12) == 0)
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
