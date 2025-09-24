package woflo.petsplus.mood.providers;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import woflo.petsplus.api.mood.EmotionProvider;
import woflo.petsplus.api.mood.MoodAPI;
import woflo.petsplus.state.OwnerCombatState;
import woflo.petsplus.state.PetComponent;

/**
 * Threat/Combat provider:
 * - Nearby hostile entities → ANGST
 * - Owner recently damaged → PROTECTIVENESS
 * - Pet recently damaged → STARTLE/FRUSTRATION
 */
public class CombatThreatProvider implements EmotionProvider {
    @Override public String id() { return "combat_threat"; }
    @Override public int periodHintTicks() { return 20; } // ~1s

    private static final int HABITUATION_WINDOW = 200; // 10s of shared context
    private static final int MEMORY_FADE_TICKS = 400;   // 20s before streaks soften

    @Override
    public void contribute(ServerWorld world, MobEntity pet, PetComponent comp, long time, MoodAPI api) {
        float resilience = comp.computeBondResilience(time);

        Box box = pet.getBoundingBox().expand(8.0);
        var hostiles = world.getEntitiesByClass(LivingEntity.class, box, e ->
            e.getType().isIn(EntityTypeTags.RAIDERS)
                || e.getType().isIn(EntityTypeTags.SKELETONS)
                || e.getType().isIn(EntityTypeTags.ZOMBIES));

        int hostileCount = hostiles.size();
        boolean hasHostiles = hostileCount > 0;

        long lastThreatTick = comp.getStateData(PetComponent.StateKeys.THREAT_LAST_TICK, Long.class, Long.MIN_VALUE);
        int safeStreak = comp.getStateData(PetComponent.StateKeys.THREAT_SAFE_STREAK, Integer.class, 0);
        int sensitizedStreak = comp.getStateData(PetComponent.StateKeys.THREAT_SENSITIZED_STREAK, Integer.class, 0);
        boolean lastEncounterDanger = comp.getStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, Boolean.class, Boolean.FALSE);
        long lastRecoveryTick = comp.getStateData(PetComponent.StateKeys.THREAT_LAST_RECOVERY_TICK, Long.class, 0L);

        var owner = comp.getOwner();
        OwnerCombatState ocs = owner != null ? OwnerCombatState.get(owner) : null;
        boolean ownerDamaged = ocs != null && ocs.recentlyDamaged(time, 60);
        if (ownerDamaged) {
            float protectiveness = 0.06f * (0.75f + 0.5f * resilience);
            api.pushEmotion(pet, PetComponent.Emotion.PROTECTIVENESS, protectiveness);
        }

        boolean petRecentlyHurt = pet.hurtTime > 0;

        if (hasHostiles) {
            boolean contiguous = lastThreatTick != Long.MIN_VALUE && (time - lastThreatTick) <= HABITUATION_WINDOW;
            boolean danger = ownerDamaged || petRecentlyHurt;

            if (danger) {
                sensitizedStreak = contiguous ? Math.min(sensitizedStreak + 1, 5) : 1;
                safeStreak = 0;
            } else {
                safeStreak = contiguous ? Math.min(safeStreak + 1, 5) : 1;
                if (!contiguous && sensitizedStreak > 0) {
                    sensitizedStreak = Math.max(0, sensitizedStreak - 1);
                }
            }

            float angstPush = Math.min(0.05f * hostileCount, 0.15f);
            if (danger) {
                float amp = 1f + 0.15f * sensitizedStreak;
                angstPush *= MathHelper.clamp(amp, 1f, 1.6f);
            } else {
                float damp = 1f - (0.12f * safeStreak) - (0.1f * resilience);
                angstPush *= MathHelper.clamp(damp, 0.35f, 1f);
            }
            api.pushEmotion(pet, PetComponent.Emotion.ANGST, angstPush);

            if (!danger && safeStreak > 0) {
                float bleed = Math.min(angstPush, (0.025f + 0.015f * safeStreak) * resilience);
                api.pushEmotion(pet, PetComponent.Emotion.ANGST, -bleed);
                api.pushEmotion(pet, PetComponent.Emotion.STARTLE, -bleed * 0.7f);
                api.pushEmotion(pet, PetComponent.Emotion.RELIEF, bleed * 0.6f);
            }

            comp.setStateData(PetComponent.StateKeys.THREAT_SAFE_STREAK, safeStreak);
            comp.setStateData(PetComponent.StateKeys.THREAT_SENSITIZED_STREAK, sensitizedStreak);
            comp.setStateData(PetComponent.StateKeys.THREAT_LAST_TICK, time);
            comp.setStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, danger);
        } else {
            if (lastThreatTick != Long.MIN_VALUE) {
                long sinceLast = time - lastThreatTick;
                if (!lastEncounterDanger && sinceLast <= 120 && (time - lastRecoveryTick) > 80) {
                    float relief = (0.02f + 0.01f * safeStreak) * resilience;
                    api.pushEmotion(pet, PetComponent.Emotion.RELIEF, relief);
                    api.pushEmotion(pet, PetComponent.Emotion.LAGOM, relief * 0.5f);
                    comp.setStateData(PetComponent.StateKeys.THREAT_LAST_RECOVERY_TICK, time);
                }

                if (sinceLast > MEMORY_FADE_TICKS) {
                    if (safeStreak > 0) {
                        safeStreak = Math.max(0, safeStreak - 1);
                        comp.setStateData(PetComponent.StateKeys.THREAT_SAFE_STREAK, safeStreak);
                    }
                    if (sensitizedStreak > 0) {
                        sensitizedStreak = Math.max(0, sensitizedStreak - 1);
                        comp.setStateData(PetComponent.StateKeys.THREAT_SENSITIZED_STREAK, sensitizedStreak);
                    }
                    if (sinceLast > MEMORY_FADE_TICKS * 2L) {
                        comp.setStateData(PetComponent.StateKeys.THREAT_LAST_TICK, Long.MIN_VALUE);
                        comp.setStateData(PetComponent.StateKeys.THREAT_LAST_DANGER, false);
                    }
                }
            }
        }

        if (petRecentlyHurt) {
            float harmAmp = 1f + 0.1f * MathHelper.clamp(sensitizedStreak, 0, 5);
            api.pushEmotion(pet, PetComponent.Emotion.STARTLE, 0.04f * harmAmp);
            api.pushEmotion(pet, PetComponent.Emotion.FRUSTRATION, 0.02f * harmAmp);
        }
    }
}
