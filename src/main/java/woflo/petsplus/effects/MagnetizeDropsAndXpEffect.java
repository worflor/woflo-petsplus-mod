package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.config.PetsPlusConfig;

import java.util.List;

/**
 * Effect that magnetizes item drops and experience orbs toward the owner.
 */
public class MagnetizeDropsAndXpEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "magnetize_drops_and_xp");
    
    private final double radius;
    private final int durationTicks;
    
    public MagnetizeDropsAndXpEffect(double radius, int durationTicks) {
        this.radius = radius;
        this.durationTicks = durationTicks;
    }
    
    public MagnetizeDropsAndXpEffect(JsonObject config) {
        this.radius = config.has("radius") ? config.get("radius").getAsDouble() : 12.0;
        this.durationTicks = config.has("duration_ticks") ? 
            config.get("duration_ticks").getAsInt() : 
            PetsPlusConfig.getInstance().getInt("scout", "lootWispDurationTicks", 80);
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();
        if (!(context.getWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        
        // Start magnetization effect for the specified duration
        scheduleMagnetization(serverWorld, owner, radius, durationTicks);
        return true;
    }
    
    @Override
    public int getDurationTicks() {
        return durationTicks;
    }
    
    private void scheduleMagnetization(ServerWorld world, PlayerEntity owner, double radius, int duration) {
        // In a real implementation, this would schedule periodic magnetization
        // For now, we'll do immediate magnetization
        magnetizeNearbyItems(world, owner, radius);
    }
    
    private void magnetizeNearbyItems(ServerWorld world, PlayerEntity owner, double radius) {
        Vec3d ownerPos = owner.getPos();
        Box searchBox = Box.of(ownerPos, radius * 2, radius * 2, radius * 2);
        
        // Magnetize item entities
        List<ItemEntity> items = world.getEntitiesByClass(
            ItemEntity.class,
            searchBox,
            item -> item.squaredDistanceTo(owner) <= radius * radius && !item.cannotPickup()
        );
        
        for (ItemEntity item : items) {
            magnetizeToPlayer(item, owner);
        }
        
        // Magnetize experience orbs
        List<ExperienceOrbEntity> orbs = world.getEntitiesByClass(
            ExperienceOrbEntity.class,
            searchBox,
            orb -> orb.squaredDistanceTo(owner) <= radius * radius
        );
        
        for (ExperienceOrbEntity orb : orbs) {
            magnetizeToPlayer(orb, owner);
        }
    }
    
    private void magnetizeToPlayer(ItemEntity item, PlayerEntity player) {
        Vec3d direction = player.getPos().subtract(item.getPos()).normalize();
        Vec3d velocity = direction.multiply(0.1); // Gentle pull
        item.setVelocity(velocity);
    }
    
    private void magnetizeToPlayer(ExperienceOrbEntity orb, PlayerEntity player) {
        Vec3d direction = player.getPos().subtract(orb.getPos()).normalize();
        Vec3d velocity = direction.multiply(0.15); // Slightly faster pull for XP
        orb.setVelocity(velocity);
    }
}