package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.roles.eepyeeper.EepyEeperCore;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.util.ChanceValidationUtil;

/**
 * Applies the Restful Dreams sleep bonuses when an owner successfully rests.
 */
public class EepyRestfulDreamsEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "eepy_restful_dreams");

    private final double searchRadius;
    private final int ownerRegenDuration;
    private final int ownerRegenAmplifier;
    private final int ownerSaturationDuration;
    private final int ownerSaturationAmplifier;
    private final int petResistanceDuration;
    private final int petResistanceAmplifier;

    public EepyRestfulDreamsEffect(JsonObject json) {
        this.searchRadius = Math.max(0.5D, RegistryJsonHelper.getDouble(json, "search_radius", 16.0D));
        this.ownerRegenDuration = Math.max(0, RegistryJsonHelper.getInt(json, "owner_regen_duration_ticks", 200));
        this.ownerRegenAmplifier = Math.max(0, RegistryJsonHelper.getInt(json, "owner_regen_amplifier", 0));
        this.ownerSaturationDuration = Math.max(0, RegistryJsonHelper.getInt(json, "owner_saturation_duration_ticks", 200));
        this.ownerSaturationAmplifier = Math.max(0, RegistryJsonHelper.getInt(json, "owner_saturation_amplifier", 1));
        this.petResistanceDuration = Math.max(0, RegistryJsonHelper.getInt(json, "pet_resistance_duration_ticks", 200));
        this.petResistanceAmplifier = Math.max(0, RegistryJsonHelper.getInt(json, "pet_resistance_amplifier", 0));
    }

    public EepyRestfulDreamsEffect() {
        this.searchRadius = 16.0D;
        this.ownerRegenDuration = 200;
        this.ownerRegenAmplifier = 0;
        this.ownerSaturationDuration = 200;
        this.ownerSaturationAmplifier = 1;
        this.petResistanceDuration = 200;
        this.petResistanceAmplifier = 0;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!"owner_sleep_complete".equals(context.getTriggerContext().getEventType())) {
            return false;
        }
        ServerWorld world = context.getWorld();
        if (world == null) {
            return false;
        }
        PlayerEntity ownerEntity = context.getOwner();
        if (!(ownerEntity instanceof ServerPlayerEntity owner)) {
            return false;
        }

        Long worldTime = context.getTriggerContext().getData("sleep_world_time", Long.class);
        if (worldTime == null) {
            worldTime = world.getTime();
        }
        UUID eventId = context.getTriggerContext().getData("sleep_event_id", UUID.class);
        if (!EepyEeperCore.beginSleepEvent(owner, eventId, worldTime)) {
            return false;
        }

        owner.getHungerManager().setFoodLevel(20);
        owner.getHungerManager().setSaturationLevel(20.0F);

        List<MobEntity> pets = EepyEeperCore.findNearbyEepyEepers(owner, searchRadius);
        if (pets.isEmpty()) {
            return false;
        }

        boolean sharedAny = false;
        boolean restfulDreamsActive = false;
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        double levelChance = config.getRoleDouble(PetRoleType.EEPY_EEPER.id(), "sleepLevelUpChance", 0.5D);
        float validatedChance = ChanceValidationUtil.getValidatedChance((float) levelChance, "eepy_eeper.sleepLevelUpChance");
        int bonusXp = config.getRoleInt(PetRoleType.EEPY_EEPER.id(), "bonusPetXpPerSleep", 25);

        for (MobEntity pet : pets) {
            if (pet == null || pet.isRemoved()) {
                continue;
            }
            PetComponent component = PetComponent.get(pet);
            if (component == null) {
                continue;
            }
            pet.setHealth(pet.getMaxHealth());

            if (component.getLevel() < 30 && !component.isWaitingForTribute()
                && ChanceValidationUtil.checkChance(validatedChance, world.random)) {
                if (EepyEeperCore.handleSleepLevelUp(component, owner, pet, world)) {
                    String petName = pet.hasCustomName()
                        ? pet.getCustomName().getString()
                        : pet.getType().getName().getString();
                    owner.sendMessage(Text.of("§6✨ " + petName + " §egained a level while dreaming! Sweet dreams grant wisdom. §6✨"), false);
                    world.playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 0.8F, 1.5F);
                    world.spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                        pet.getX(), pet.getY() + pet.getHeight() * 0.8, pet.getZ(),
                        8, 0.5, 0.3, 0.5, 0.05);
                }
            }

            if (component.getLevel() >= 20) {
                if (ownerRegenDuration > 0) {
                    owner.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, ownerRegenDuration, ownerRegenAmplifier));
                }
                if (ownerSaturationDuration > 0) {
                    owner.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, ownerSaturationDuration, ownerSaturationAmplifier));
                }
                if (petResistanceDuration > 0) {
                    pet.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, petResistanceDuration, petResistanceAmplifier));
                }
                component.addExperience(bonusXp);
                owner.sendMessage(Text.translatable("petsplus.eepyeeper.sleep_bonus"), true);
                restfulDreamsActive = true;
            }

            EepyEeperCore.emitSleepLinkParticles(owner, pet, component.getLevel() >= 20);
            sharedAny = true;
        }

        if (sharedAny) {
            EepyEeperCore.playSleepShareSound(world, owner, restfulDreamsActive);
            EepyEeperCore.processSleepRecovery(owner, world);
        }

        return sharedAny;
    }
}
