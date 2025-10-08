package woflo.petsplus.effects;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.state.OwnerCombatState;

/**
 * Effect that adds a bonus to the owner's next attack.
 */
public class OwnerNextAttackBonusEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "owner_next_attack_bonus");
    
    private final double bonusDamagePct;
    private final String vsTag;
    private final Effect onHitEffect;
    private final int expireTicks;
    
    public OwnerNextAttackBonusEffect(double bonusDamagePct, String vsTag, Effect onHitEffect, int expireTicks) {
        this.bonusDamagePct = bonusDamagePct;
        this.vsTag = vsTag;
        this.onHitEffect = onHitEffect;
        this.expireTicks = expireTicks;
    }
    
    public OwnerNextAttackBonusEffect(double bonusDamagePct) {
        this(bonusDamagePct, null, null, 100); // Default 5 second expiry
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        PlayerEntity owner = context.getOwner();
        if (owner == null || !owner.isAlive() || owner.isRemoved()) {
            return false;
        }

        ServerWorld world = context.getEntityWorld();
        if (world == null) {
            if (owner.getEntityWorld() instanceof ServerWorld ownerWorld) {
                world = ownerWorld;
            } else {
                return false;
            }
        }

        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);

        // Create attack rider data
        AttackRiderData riderData = new AttackRiderData(
            bonusDamagePct,
            vsTag,
            onHitEffect,
            world.getTime() + expireTicks
        );

        combatState.addNextAttackRider("damage_bonus", riderData);
        return true;
    }
    
    @Override
    public int getDurationTicks() {
        return expireTicks;
    }
    
    /**
     * Data class for attack rider information.
     */
    public static class AttackRiderData {
        public final double bonusDamagePct;
        public final String vsTag;
        public final Effect onHitEffect;
        public final long expiryTick;
        
        public AttackRiderData(double bonusDamagePct, String vsTag, Effect onHitEffect, long expiryTick) {
            this.bonusDamagePct = bonusDamagePct;
            this.vsTag = vsTag;
            this.onHitEffect = onHitEffect;
            this.expiryTick = expiryTick;
        }
        
        public boolean isExpired(long currentTick) {
            return currentTick >= expiryTick;
        }
        
        public boolean appliesToTarget(Entity target) {
            if (vsTag == null) return true;
            return woflo.petsplus.util.EntityTagUtil.hasTag(target, vsTag);
        }
    }
}

