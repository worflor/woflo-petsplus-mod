package woflo.petsplus.mixin;

import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mixin to provide access to protected fields in MobEntity for AI enhancements.
 */
@Mixin(MobEntity.class)
public interface MobEntityAccessor {
    
    @Accessor("goalSelector")
    GoalSelector getGoalSelector();
    
    @Accessor("targetSelector")
    GoalSelector getTargetSelector();
}
