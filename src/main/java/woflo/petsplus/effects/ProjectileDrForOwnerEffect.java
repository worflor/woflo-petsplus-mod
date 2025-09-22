package woflo.petsplus.effects;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Effect that provides projectile damage reduction for the owner.
 */
public class ProjectileDrForOwnerEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "projectile_dr_for_owner");
    private static final Map<PlayerEntity, ProjectileDrData> ACTIVE_DR = new HashMap<>();
    
    private final double percent;
    private final int durationTicks;
    
    public ProjectileDrForOwnerEffect(double percent, int durationTicks) {
        this.percent = percent;
        this.durationTicks = durationTicks;
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();
        long expiryTime = context.getWorld().getTime() + durationTicks;
        
        ACTIVE_DR.put(owner, new ProjectileDrData(percent, expiryTime));
        return true;
    }
    
    @Override
    public int getDurationTicks() {
        return durationTicks;
    }
    
    /**
     * Get the projectile damage reduction for a player.
     */
    public static double getProjectileDr(PlayerEntity player) {
        ProjectileDrData data = ACTIVE_DR.get(player);
        if (data == null) return 0.0;
        
        long currentTime = player.getWorld().getTime();
        if (currentTime >= data.expiryTime) {
            ACTIVE_DR.remove(player);
            return 0.0;
        }
        
        return data.percent;
    }
    
    /**
     * Clean up expired damage reduction effects.
     */
    public static void cleanupExpired(long currentTime) {
        ACTIVE_DR.entrySet().removeIf(entry -> currentTime >= entry.getValue().expiryTime);
    }
    
    private static class ProjectileDrData {
        final double percent;
        final long expiryTime;
        
        ProjectileDrData(double percent, long expiryTime) {
            this.percent = percent;
            this.expiryTime = expiryTime;
        }
    }
}