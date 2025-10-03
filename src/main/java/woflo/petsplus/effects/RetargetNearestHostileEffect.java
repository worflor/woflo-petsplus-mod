package woflo.petsplus.effects;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;

import java.util.List;

/**
 * Effect that retargets to the nearest hostile entity within radius.
 */
public class RetargetNearestHostileEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "retarget_nearest_hostile");
    
    private final double radius;
    private final String storeAs;
    @Nullable
    private final String requiredDataFlag;
    private final boolean requiredDataValue;
    private final boolean requireFlag;

    public RetargetNearestHostileEffect(double radius, String storeAs) {
        this(radius, storeAs, null, true);
    }

    public RetargetNearestHostileEffect(double radius,
                                        String storeAs,
                                        @Nullable String requiredDataFlag,
                                        boolean requiredDataValue) {
        this.radius = radius;
        this.storeAs = storeAs;
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
        Entity pet = context.getPet();

        if (requireFlag) {
            Boolean flagValue = context.getData(requiredDataFlag, Boolean.class);
            boolean matches = flagValue != null && flagValue;
            if (matches != requiredDataValue) {
                return false;
            }
        }

        // Create bounding box around pet
        Box searchBox = Box.of(pet.getPos(), radius * 2, radius * 2, radius * 2);
        
        // Find all hostile entities in range
        List<LivingEntity> entities = context.getWorld().getEntitiesByClass(
            LivingEntity.class, 
            searchBox, 
            entity -> entity instanceof HostileEntity && 
                     entity.isAlive() && 
                     pet.squaredDistanceTo(entity) <= radius * radius
        );
        
        if (entities.isEmpty()) {
            return false;
        }
        
        // Find closest entity
        LivingEntity closest = entities.stream()
            .min((a, b) -> Double.compare(pet.squaredDistanceTo(a), pet.squaredDistanceTo(b)))
            .orElse(null);
        
        if (closest != null) {
            // Store the target for use by other effects
            context.withData(storeAs, closest);
            return true;
        }
        
        return false;
    }
}