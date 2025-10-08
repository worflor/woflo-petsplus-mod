package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
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
 * Effect that handles the Cursed One owner's sacrificial rescue by routing the
 * existing resurrection logic through the ability system.
 */
public class CursedOneSacrificialRescueEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "cursed_one_sacrificial_rescue");

    private final double activationRadiusSq;
    private final int minimumLevel;

    public CursedOneSacrificialRescueEffect(JsonObject json) {
        double radius = RegistryJsonHelper.getDouble(json, "activation_radius", 16.0D);
        this.activationRadiusSq = Math.max(1.0D, radius) * Math.max(1.0D, radius);
        this.minimumLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 15));
    }

    public CursedOneSacrificialRescueEffect() {
        this.activationRadiusSq = 16.0D * 16.0D;
        this.minimumLevel = 15;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        DamageInterceptionResult interception = context.getDamageResult();
        if (interception == null || interception.isCancelled()) {
            return false;
        }
        if (!context.isLethalDamage() || !context.hasDamageContext()) {
            return false;
        }

        ServerWorld world = context.getEntityWorld();
        MobEntity pet = context.getPet();
        if (world == null || pet == null) {
            return false;
        }

        if (!(context.getOwner() instanceof ServerPlayerEntity owner)) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.isOwnedBy(owner)) {
            return false;
        }
        if (!component.hasRole(PetRoleType.CURSED_ONE) || component.getLevel() < minimumLevel) {
            return false;
        }

        if (owner.squaredDistanceTo(pet) > activationRadiusSq) {
            return false;
        }

        if (!CursedOneResurrection.canPetResurrectOwner(pet)) {
            return false;
        }

        double radius = Math.sqrt(activationRadiusSq);
        List<MobEntity> candidates = CursedOneResurrection.findNearbyCursedOnePets(owner, world, radius);
        MobEntity bestCandidate = CursedOneResurrection.findBestResurrectionPet(candidates);
        if (bestCandidate == null || bestCandidate != pet) {
            return false;
        }

        float chance = CursedOneResurrection.getResurrectionChance(pet);
        if (chance <= 0.0F || world.getRandom().nextFloat() > chance) {
            return false;
        }

        DamageSource damageSource = context.getIncomingDamageSource();
        if (damageSource == null) {
            damageSource = owner.getDamageSources().generic();
        }

        if (!CursedOneResurrection.performAutoResurrection(owner, pet, damageSource)) {
            return false;
        }

        interception.cancel();
        return true;
    }
}


