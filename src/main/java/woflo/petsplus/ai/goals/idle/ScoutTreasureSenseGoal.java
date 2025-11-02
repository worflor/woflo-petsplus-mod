package woflo.petsplus.ai.goals.idle;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import woflo.petsplus.ai.goals.AdaptiveGoal;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.api.registry.PetRoleType;

import java.util.EnumSet;

/**
 * Scout passive: Emits subtle particles when near valuable blocks.
 * Adds "treasure sense" flavor - scouts can smell riches.
 */
public class ScoutTreasureSenseGoal extends AdaptiveGoal {
    private static final double DETECTION_RADIUS = 8.0;
    private static final int PARTICLE_INTERVAL = 40; // Every 2 seconds
    
    private int tickCounter;
    private boolean nearTreasure;
    
    public ScoutTreasureSenseGoal(MobEntity mob, GoalDefinition goalDefinition) {
        super(mob, goalDefinition, EnumSet.noneOf(Control.class)); // Passive detection only
    }
    
    @Override
    protected boolean canStartGoal() {
        // Only for Scout role
        if (petComponent == null || !petComponent.hasRole(PetRoleType.SCOUT)) {
            return false;
        }
        
        // Low priority background task
        if (mob.getRandom().nextInt(100) != 0) {
            return false;
        }
        
        return true;
    }
    
    @Override
    protected boolean shouldContinueGoal() {
        return tickCounter < 200; // Run for 10 seconds then re-check
    }
    
    @Override
    protected void onStartGoal() {
        tickCounter = 0;
        nearTreasure = false;
    }
    
    @Override
    protected void onStopGoal() {
        tickCounter = 0;
    }
    
    @Override
    protected void onTickGoal() {
        tickCounter++;
        
        // Check periodically
        if (tickCounter % PARTICLE_INTERVAL == 0) {
            nearTreasure = detectNearbyTreasure();
            
            if (nearTreasure && mob.getEntityWorld() instanceof ServerWorld sw) {
                // Very subtle golden sparkle near head
                sw.spawnParticles(
                    ParticleTypes.WAX_ON,
                    mob.getX() + (mob.getRandom().nextDouble() - 0.5) * 0.3,
                    mob.getY() + mob.getStandingEyeHeight() - 0.1,
                    mob.getZ() + (mob.getRandom().nextDouble() - 0.5) * 0.3,
                    1, 0.0, 0.0, 0.0, 0.0
                );
            }
        }
    }
    
    @Override
    protected float calculateEngagement() {
        return nearTreasure ? 0.3f : 0.1f;
    }
    
    private boolean detectNearbyTreasure() {
        if (!(mob.getEntityWorld() instanceof ServerWorld sw)) {
            return false;
        }
        
        BlockPos mobPos = mob.getBlockPos();
        int radius = (int) DETECTION_RADIUS;
        
        // Check for valuable blocks in a sphere around the scout
        for (BlockPos pos : BlockPos.iterateOutwards(mobPos, radius, radius, radius)) {
            double distSq = pos.getSquaredDistance(mobPos);
            if (distSq > DETECTION_RADIUS * DETECTION_RADIUS) {
                continue;
            }
            
            BlockState state = sw.getBlockState(pos);
            Block block = state.getBlock();
            
            // Check for ores
            if (state.isIn(BlockTags.DIAMOND_ORES) ||
                state.isIn(BlockTags.GOLD_ORES) ||
                state.isIn(BlockTags.EMERALD_ORES) ||
                block == Blocks.ANCIENT_DEBRIS) {
                return true;
            }
            
            // Check for lootable containers
            BlockEntity be = sw.getBlockEntity(pos);
            if (be instanceof LootableContainerBlockEntity lootable) {
                // Simplified check - any container is interesting
                if (!lootable.isEmpty()) {
                    return true;
                }
            }
        }
        
        return false;
    }
}
