package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.roles.eclipsed.EclipsedVoid;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.ui.UIFeedbackManager;

/**
 * Tags a nearby hostile target after combat ends so the owner can blink to them with their
 * Eclipsed companion.
 */
public class EclipsedPhasePartnerEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "eclipsed_phase_partner");

    private final double radius;
    private final int tagDurationTicks;
    private final String tagKey;
    private final int minLevel;
    private final boolean requirePerched;

    public EclipsedPhasePartnerEffect(JsonObject json) {
        this.radius = Math.max(1.0D, RegistryJsonHelper.getDouble(json, "radius", 12.0D));
        this.tagDurationTicks = Math.max(20, RegistryJsonHelper.getInt(json, "tag_duration_ticks", EclipsedVoid.getMarkDurationTicks()));
        this.tagKey = RegistryJsonHelper.getString(json, "tag_key", "petsplus:phase_partner");
        this.minLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 23));
        this.requirePerched = RegistryJsonHelper.getBoolean(json, "require_perched", true);
    }

    public EclipsedPhasePartnerEffect() {
        this.radius = 12.0D;
        this.tagDurationTicks = EclipsedVoid.getMarkDurationTicks();
        this.tagKey = "petsplus:phase_partner";
        this.minLevel = 23;
        this.requirePerched = true;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        ServerWorld world = context.getEntityWorld();
        MobEntity pet = context.getPet();
        PlayerEntity owner = context.getOwner();
        if (world == null || pet == null || owner == null) {
            return false;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.ECLIPSED) || component.getLevel() < minLevel) {
            return false;
        }

        if (requirePerched && !EclipsedVoid.isPetPerched(pet, owner)) {
            return false;
        }

        Vec3d center = owner.getEntityPos();
        Box search = Box.of(center, radius * 2, radius * 2, radius * 2);
        List<HostileEntity> hostiles = world.getEntitiesByClass(HostileEntity.class, search, entity -> entity.isAlive() && entity.squaredDistanceTo(center) <= radius * radius);
        if (hostiles.isEmpty()) {
            return false;
        }

        HostileEntity target = hostiles.stream().min(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(center))).orElse(null);
        if (target == null) {
            return false;
        }

        context.withData("phase_partner_target", target);
        TagTargetEffect tagEffect = new TagTargetEffect("phase_partner_target", tagKey, tagDurationTicks);
        tagEffect.execute(context);

        OwnerCombatState combatState = OwnerCombatState.getOrCreate(owner);
        combatState.rememberAggroTarget(target, world.getTime(), tagDurationTicks);

        // Removed phase partner spam - will add particle/sound in ability JSON

        emitFeedback(world, pet, target);
        return true;
    }

    private void emitFeedback(ServerWorld world, MobEntity pet, Entity target) {
        Vec3d start = pet.getEntityPos();
        Vec3d end = target.getEntityPos();
        for (int i = 0; i <= 8; i++) {
            double t = i / 8.0D;
            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t + 0.5D;
            double z = start.z + (end.z - start.z) * t;
            world.spawnParticles(ParticleTypes.CRIT, x, y, z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
        world.playSound(null, start.x, start.y, start.z, SoundEvents.ENTITY_ENDERMAN_AMBIENT, SoundCategory.NEUTRAL, 0.4F, 1.2F);
    }
}



