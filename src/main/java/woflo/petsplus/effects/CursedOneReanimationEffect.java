package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.mechanics.CursedOneResurrection;
import woflo.petsplus.state.PetComponent;

/**
 * Ability effect that transitions a Cursed One pet into its reanimation state when lethal damage
 * is intercepted through the ability system.
 */
public class CursedOneReanimationEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "cursed_one_reanimation");

    private final int minimumLevel;

    public CursedOneReanimationEffect(JsonObject json) {
        this.minimumLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 15));
    }

    public CursedOneReanimationEffect() {
        this.minimumLevel = 15;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!context.hasDamageContext() || !context.isLethalDamage()) {
            return false;
        }
        DamageInterceptionResult interception = context.getDamageResult();
        if (interception == null || interception.isCancelled()) {
            return false;
        }

        MobEntity pet = context.getPet();
        if (pet == null || !pet.isAlive()) {
            return false;
        }
        if (!(context.getWorld() instanceof ServerWorld)) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.CURSED_ONE) || component.getLevel() < minimumLevel) {
            return false;
        }
        if (CursedOneResurrection.isReanimating(pet)) {
            return false;
        }

        DamageSource damageSource = context.getIncomingDamageSource();
        if (damageSource == null) {
            if (context.getOwner() != null) {
                damageSource = context.getOwner().getDamageSources().generic();
            }
            if (damageSource == null) {
                damageSource = pet.getDamageSources().generic();
            }
        }

        if (!CursedOneResurrection.tryBeginReanimation(pet, component, damageSource)) {
            return false;
        }

        interception.cancel();
        return true;
    }
}
