package woflo.petsplus.effects;

import net.minecraft.entity.Entity;
import net.minecraft.particle.DragonBreathParticleEffect;
import net.minecraft.particle.EffectParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;

/**
 * Spawns particles at entity or victim location.
 * Highly configurable for creating distinct visual feedback per ability.
 */
public class ParticleEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "particle_effect");
    
    private final String particleType;
    private final String targetEntity; // "pet", "owner", "victim"
    private final int count;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double speed;
    private final double heightOffset; // Offset from entity feet (e.g., 0.5 for center, 1.0 for head)
    private final boolean followEntity; // If true, centered on entity; if false, uses offset as absolute position
    
    public ParticleEffect(String particleType, String targetEntity, int count, 
                         double offsetX, double offsetY, double offsetZ, 
                         double speed, double heightOffset) {
        this.particleType = particleType;
        this.targetEntity = targetEntity != null ? targetEntity : "victim";
        this.count = count;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.speed = speed;
        this.heightOffset = heightOffset;
        this.followEntity = true;
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getEntityWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        
        Entity targetEntity = resolveTarget(context);
        if (targetEntity == null) {
            return false;
        }
        
        Vec3d pos = targetEntity.getEntityPos();
        double height = targetEntity.getHeight();
        
        // Parse particle type
        net.minecraft.particle.ParticleEffect particle = parseParticleType(particleType);
        if (particle == null) {
            return false;
        }
        
        // Spawn particles
        serverWorld.spawnParticles(
            particle,
            pos.x, 
            pos.y + (height * heightOffset), 
            pos.z,
            count,
            offsetX,
            offsetY,
            offsetZ,
            speed
        );
        
        return true;
    }
    
    private Entity resolveTarget(EffectContext context) {
        return switch (targetEntity.toLowerCase()) {
            case "pet" -> context.getPet();
            case "owner" -> context.getOwner();
            case "victim" -> context.getData("victim", Entity.class);
            default -> null;
        };
    }
    
    private net.minecraft.particle.ParticleEffect parseParticleType(String type) {
        // Map common particle names to actual particle types
        return switch (type.toLowerCase()) {
            case "enchanted_hit" -> ParticleTypes.ENCHANTED_HIT;
            case "crit" -> ParticleTypes.CRIT;
            case "enchant" -> ParticleTypes.ENCHANT;
            case "smoke" -> ParticleTypes.SMOKE;
            case "cloud" -> ParticleTypes.CLOUD;
            case "soul" -> ParticleTypes.SOUL;
            case "soul_fire_flame" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "flame" -> ParticleTypes.FLAME;
            case "end_rod" -> ParticleTypes.END_ROD;
            case "portal" -> ParticleTypes.PORTAL;
            case "dragon_breath" -> DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F);
            case "sweep_attack" -> ParticleTypes.SWEEP_ATTACK;
            case "damage_indicator" -> ParticleTypes.DAMAGE_INDICATOR;
            case "angry_villager" -> ParticleTypes.ANGRY_VILLAGER;
            case "happy_villager" -> ParticleTypes.HAPPY_VILLAGER;
            case "explosion" -> ParticleTypes.EXPLOSION;
            case "explosion_emitter" -> ParticleTypes.EXPLOSION_EMITTER;
            case "totem_of_undying" -> ParticleTypes.TOTEM_OF_UNDYING;
            case "scrape" -> ParticleTypes.SCRAPE;
            case "wax_on" -> ParticleTypes.WAX_ON;
            case "electric_spark" -> ParticleTypes.ELECTRIC_SPARK;
            case "snowflake" -> ParticleTypes.SNOWFLAKE;
            case "gust_emitter_small" -> ParticleTypes.GUST_EMITTER_SMALL;
            case "gust_emitter_large" -> ParticleTypes.GUST_EMITTER_LARGE;
            case "trial_spawner_detection" -> ParticleTypes.TRIAL_SPAWNER_DETECTION;
            case "vault_connection" -> ParticleTypes.VAULT_CONNECTION;
            case "infested" -> ParticleTypes.INFESTED;
            case "item_cobweb" -> ParticleTypes.ITEM_COBWEB;
            case "white_smoke" -> ParticleTypes.WHITE_SMOKE;
            case "dust_plume" -> ParticleTypes.DUST_PLUME;
            case "gust" -> ParticleTypes.GUST;
            case "small_gust" -> ParticleTypes.SMALL_GUST;
            case "reverse_portal" -> ParticleTypes.REVERSE_PORTAL;
            case "squid_ink" -> ParticleTypes.SQUID_INK;
            case "underwater" -> ParticleTypes.UNDERWATER;
            case "bubble" -> ParticleTypes.BUBBLE;
            case "bubble_pop" -> ParticleTypes.BUBBLE_POP;
            case "bubble_column_up" -> ParticleTypes.BUBBLE_COLUMN_UP;
            case "nautilus" -> ParticleTypes.NAUTILUS;
            case "dolphin" -> ParticleTypes.DOLPHIN;
            case "firework" -> ParticleTypes.FIREWORK;
            case "flash" -> TintedParticleEffect.create(ParticleTypes.FLASH, 1.0F, 1.0F, 1.0F);
            case "falling_nectar" -> ParticleTypes.FALLING_NECTAR;
            case "falling_honey" -> ParticleTypes.FALLING_HONEY;
            case "landing_honey" -> ParticleTypes.LANDING_HONEY;
            case "falling_water" -> ParticleTypes.FALLING_WATER;
            case "falling_lava" -> ParticleTypes.FALLING_LAVA;
            case "dripping_water" -> ParticleTypes.DRIPPING_WATER;
            case "dripping_lava" -> ParticleTypes.DRIPPING_LAVA;
            case "dripping_honey" -> ParticleTypes.DRIPPING_HONEY;
            case "dripping_obsidian_tear" -> ParticleTypes.DRIPPING_OBSIDIAN_TEAR;
            case "cherry_leaves" -> ParticleTypes.CHERRY_LEAVES;
            case "pale_oak_leaves" -> ParticleTypes.PALE_OAK_LEAVES;
            case "rain" -> ParticleTypes.RAIN;
            case "lava" -> ParticleTypes.LAVA;
            case "mycelium" -> ParticleTypes.MYCELIUM;
            case "note" -> ParticleTypes.NOTE;
            case "poof" -> ParticleTypes.POOF;
            case "large_smoke" -> ParticleTypes.LARGE_SMOKE;
            case "effect" -> EffectParticleEffect.of(ParticleTypes.EFFECT, 1.0F, 1.0F, 1.0F, 1.0F);
            case "witch" -> ParticleTypes.WITCH;
            case "dripping_dripstone_water" -> ParticleTypes.DRIPPING_DRIPSTONE_WATER;
            case "falling_dripstone_water" -> ParticleTypes.FALLING_DRIPSTONE_WATER;
            case "dripping_dripstone_lava" -> ParticleTypes.DRIPPING_DRIPSTONE_LAVA;
            case "falling_dripstone_lava" -> ParticleTypes.FALLING_DRIPSTONE_LAVA;
            case "spore_blossom_air" -> ParticleTypes.SPORE_BLOSSOM_AIR;
            case "ash" -> ParticleTypes.ASH;
            case "crimson_spore" -> ParticleTypes.CRIMSON_SPORE;
            case "warped_spore" -> ParticleTypes.WARPED_SPORE;
            case "sonic_boom" -> ParticleTypes.SONIC_BOOM;
            case "sculk_soul" -> ParticleTypes.SCULK_SOUL;
            case "sculk_charge_pop" -> ParticleTypes.SCULK_CHARGE_POP;
            case "glow_squid_ink" -> ParticleTypes.GLOW_SQUID_INK;
            case "glow" -> ParticleTypes.GLOW;
            case "wax_off" -> ParticleTypes.WAX_OFF;
            case "ominous_spawning" -> ParticleTypes.OMINOUS_SPAWNING;
            case "raid_omen" -> ParticleTypes.RAID_OMEN;
            case "trial_omen" -> ParticleTypes.TRIAL_OMEN;
            default -> null;
        };
    }
}



