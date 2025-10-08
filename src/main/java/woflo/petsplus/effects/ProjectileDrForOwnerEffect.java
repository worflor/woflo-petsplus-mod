package woflo.petsplus.effects;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
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
    @Nullable
    private final String requiredDataFlag;
    private final boolean requiredDataValue;
    private final boolean requireFlag;

    public ProjectileDrForOwnerEffect(double percent, int durationTicks) {
        this(percent, durationTicks, null, true);
    }

    public ProjectileDrForOwnerEffect(double percent,
                                      int durationTicks,
                                      @Nullable String requiredDataFlag,
                                      boolean requiredDataValue) {
        this.percent = percent;
        this.durationTicks = durationTicks;
        this.requiredDataFlag = requiredDataFlag == null || requiredDataFlag.isEmpty() ? null : requiredDataFlag;
        this.requiredDataValue = requiredDataValue;
        this.requireFlag = this.requiredDataFlag != null;
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        cleanupRemovedOwners();

        if (requireFlag) {
            Boolean flagValue = context.getData(requiredDataFlag, Boolean.class);
            boolean matches = flagValue != null && flagValue;
            if (matches != requiredDataValue) {
                return false;
            }
        }

        PlayerEntity owner = context.getOwner();
        if (owner == null || owner.isRemoved() || !owner.isAlive()) {
            return false;
        }

        ServerWorld world = context.getEntityWorld();
        if (world == null) {
            World ownerWorld = owner.getEntityWorld();
            if (ownerWorld instanceof ServerWorld serverWorld) {
                world = serverWorld;
            } else {
                return false;
            }
        }

        long expiryTime = world.getTime() + durationTicks;

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
        if (player == null) {
            return 0.0;
        }

        cleanupRemovedOwners();

        ProjectileDrData data = ACTIVE_DR.get(player);
        if (data == null) {
            return 0.0;
        }

        if (player.isRemoved() || !player.isAlive()) {
            ACTIVE_DR.remove(player);
            return 0.0;
        }

        World world = player.getEntityWorld();
        if (world == null) {
            ACTIVE_DR.remove(player);
            return 0.0;
        }

        long currentTime = world.getTime();
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
        ACTIVE_DR.entrySet().removeIf(entry -> currentTime >= entry.getValue().expiryTime || entry.getKey().isRemoved());
    }

    private static void cleanupRemovedOwners() {
        ACTIVE_DR.entrySet().removeIf(entry -> entry.getKey().isRemoved());
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

