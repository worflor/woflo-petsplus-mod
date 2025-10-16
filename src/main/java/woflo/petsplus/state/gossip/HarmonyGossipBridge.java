package woflo.petsplus.state.gossip;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.relationships.RelationshipProfile;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bridges the nature harmony system with the gossip/tone pipeline so social
 * routines can modulate their reactions based on current rapport or friction
 * between participants.
 */
public final class HarmonyGossipBridge {

    private HarmonyGossipBridge() {
    }

    public static HarmonyGossipProfile evaluate(@Nullable PetComponent storyteller,
                                                @Nullable PetComponent listener) {
        if (storyteller == null || listener == null) {
            return HarmonyGossipProfile.NEUTRAL;
        }
        PetComponent.HarmonyState storytellerState = storyteller.getHarmonyState();
        PetComponent.HarmonyState listenerState = listener.getHarmonyState();

        PetComponent.HarmonyCompatibility storytellerCompatibility = resolveCompatibility(storyteller, listener);
        PetComponent.HarmonyCompatibility listenerCompatibility = resolveCompatibility(listener, storyteller);

        if (isInactive(storytellerState) && isInactive(listenerState)
            && storytellerCompatibility == null && listenerCompatibility == null) {
            return HarmonyGossipProfile.NEUTRAL;
        }
        if (storytellerState == null) {
            storytellerState = PetComponent.HarmonyState.empty();
        }
        if (listenerState == null) {
            listenerState = PetComponent.HarmonyState.empty();
        }

        float setPositiveBias = computeSharedBias(storytellerState.harmonySetIds(), listenerState.harmonySetIds(),
            storytellerState.harmonyStrength(), listenerState.harmonyStrength());
        float setNegativeBias = computeSharedBias(storytellerState.disharmonySetIds(), listenerState.disharmonySetIds(),
            storytellerState.disharmonyStrength(), listenerState.disharmonyStrength());

        float compatibilityPositive = compatibilityBias(storytellerCompatibility, listenerCompatibility, true);
        float compatibilityNegative = compatibilityBias(storytellerCompatibility, listenerCompatibility, false);

        RelationshipProfile storytellerRelationship = resolveRelationship(storyteller, listener);
        RelationshipProfile listenerRelationship = resolveRelationship(listener, storyteller);

        float storytellerBond = bondScore(storytellerRelationship);
        float listenerBond = bondScore(listenerRelationship);
        float bondScore = combineBond(storytellerBond, listenerBond);

        float synergySignal = Math.max(setPositiveBias, compatibilityPositive);
        float frictionSignal = Math.max(setNegativeBias, compatibilityNegative);
        float playfulSarcasm = computePlayfulSarcasm(bondScore, synergySignal, frictionSignal);

        float positiveBias = blendBias(setPositiveBias, compatibilityPositive);
        float negativeBias = blendBias(
            mitigateNegative(setNegativeBias, playfulSarcasm),
            mitigateNegative(compatibilityNegative, playfulSarcasm * 1.1f)
        );

        return new HarmonyGossipProfile(
            positiveBias,
            negativeBias,
            playfulSarcasm
        );
    }

    private static float computeSharedBias(List<Identifier> storytellerSets,
                                           List<Identifier> listenerSets,
                                           float storytellerStrength,
                                           float listenerStrength) {
        if (storytellerSets == null || listenerSets == null
            || storytellerSets.isEmpty() || listenerSets.isEmpty()) {
            return 0f;
        }
        Set<Identifier> listenerLookup = new HashSet<>(listenerSets);
        int shared = 0;
        for (Identifier id : storytellerSets) {
            if (listenerLookup.contains(id)) {
                shared++;
            }
        }
        if (shared <= 0) {
            return 0f;
        }
        float membership = shared / (float) Math.max(1, Math.min(storytellerSets.size(), listenerSets.size()));
        float combinedStrength = Math.min(MathHelper.clamp(storytellerStrength, 0f, 3f),
            MathHelper.clamp(listenerStrength, 0f, 3f));
        float normalizedStrength = MathHelper.clamp(combinedStrength / 2.5f, 0f, 1f);
        float bias = (membership * 0.5f) + (normalizedStrength * 0.5f);
        return MathHelper.clamp(bias, 0f, 1f);
    }

    private static boolean isInactive(@Nullable PetComponent.HarmonyState state) {
        return state == null || state.isEmpty();
    }

    @Nullable
    private static RelationshipProfile resolveRelationship(@Nullable PetComponent source,
                                                            @Nullable PetComponent target) {
        if (source == null || target == null || target.getPetEntity() == null) {
            return null;
        }
        UUID targetId = target.getPetEntity().getUuid();
        if (targetId == null) {
            return null;
        }
        return source.getRelationshipWith(targetId);
    }

    private static float bondScore(@Nullable RelationshipProfile profile) {
        if (profile == null) {
            return 0f;
        }
        float trust = MathHelper.clamp((profile.trust() + 1f) / 2f, 0f, 1f);
        float affection = MathHelper.clamp(profile.affection(), 0f, 1f);
        float respect = MathHelper.clamp(profile.respect(), 0f, 1f);
        float comfort = MathHelper.clamp(profile.getComfort(), 0f, 1f);
        float bond = (trust * 0.35f) + (affection * 0.35f) + (comfort * 0.2f) + (respect * 0.1f);
        return MathHelper.clamp(bond, 0f, 1f);
    }

    private static float combineBond(float first, float second) {
        if (first <= 0f && second <= 0f) {
            return 0f;
        }
        float total = 0f;
        int count = 0;
        if (first > 0f) {
            total += first;
            count++;
        }
        if (second > 0f) {
            total += second;
            count++;
        }
        if (count == 0) {
            return 0f;
        }
        return MathHelper.clamp(total / count, 0f, 1f);
    }

    private static float computePlayfulSarcasm(float bondScore, float synergySignal, float frictionSignal) {
        if (bondScore <= 0f && synergySignal <= 0f) {
            return 0f;
        }
        float base = (bondScore * 0.65f) + (synergySignal * 0.45f);
        float damp = frictionSignal * 0.55f;
        return MathHelper.clamp(base - damp, 0f, 1f);
    }

    private static float mitigateNegative(float bias, float playfulSarcasm) {
        if (bias <= 0f || playfulSarcasm <= 0f) {
            return bias;
        }
        float mitigation = playfulSarcasm * (0.25f + (bias * 0.15f));
        return MathHelper.clamp(bias - mitigation, 0f, 1f);
    }

    @Nullable
    private static PetComponent.HarmonyCompatibility resolveCompatibility(@Nullable PetComponent source,
                                                                           @Nullable PetComponent target) {
        if (source == null || target == null) {
            return null;
        }
        if (target.getPetEntity() == null) {
            return null;
        }
        UUID targetId = target.getPetEntity().getUuid();
        if (targetId == null) {
            return null;
        }
        return source.getHarmonyCompatibility(targetId);
    }

    private static float compatibilityBias(@Nullable PetComponent.HarmonyCompatibility primary,
                                           @Nullable PetComponent.HarmonyCompatibility secondary,
                                           boolean positive) {
        float total = 0f;
        int count = 0;
        if (primary != null) {
            float value = positive ? primary.harmonyStrength() : primary.disharmonyStrength();
            if (value > 0f) {
                total += MathHelper.clamp(value / 3.0f, 0f, 1f);
                count++;
            }
        }
        if (secondary != null) {
            float value = positive ? secondary.harmonyStrength() : secondary.disharmonyStrength();
            if (value > 0f) {
                total += MathHelper.clamp(value / 3.0f, 0f, 1f);
                count++;
            }
        }
        if (count == 0) {
            return 0f;
        }
        return MathHelper.clamp(total / count, 0f, 1f);
    }

    private static float blendBias(float setBias, float compatibilityBias) {
        if (setBias <= 0f) {
            return compatibilityBias;
        }
        if (compatibilityBias <= 0f) {
            return setBias;
        }
        float combined = (setBias * 0.55f) + (compatibilityBias * 0.65f);
        return MathHelper.clamp(combined, 0f, 1f);
    }

    public record HarmonyGossipProfile(float positiveBias, float negativeBias, float playfulSarcasm) {
        public static final HarmonyGossipProfile NEUTRAL = new HarmonyGossipProfile(0f, 0f, 0f);

        public boolean isNeutral() {
            return positiveBias <= 0.0001f && negativeBias <= 0.0001f && playfulSarcasm <= 0.0001f;
        }

        public float adjustPositive(float value) {
            if (value <= 0f) {
                return 0f;
            }
            float scale = MathHelper.clamp(
                1.0f + (positiveBias * 0.6f) + (playfulSarcasm * 0.15f) - (negativeBias * 0.7f),
                0.1f,
                1.8f
            );
            return value * scale;
        }

        public float adjustCuriosity(float value) {
            if (value <= 0f) {
                return 0f;
            }
            float scale = MathHelper.clamp(
                1.0f + (positiveBias * 0.3f) + (playfulSarcasm * 0.1f) - (negativeBias * 0.5f),
                0.1f,
                1.6f
            );
            return value * scale;
        }

        public float adjustStorytellerEcho(float value) {
            if (value <= 0f) {
                return 0f;
            }
            float scale = MathHelper.clamp(
                1.0f + (positiveBias * 0.5f) + (playfulSarcasm * 0.2f) - (negativeBias * 0.35f),
                0.1f,
                1.7f
            );
            return value * scale;
        }

        public float adjustFrustration(float value) {
            if (value <= 0f) {
                return 0f;
            }
            float mitigation = (positiveBias * 0.2f) + (playfulSarcasm * 0.5f);
            float scale = MathHelper.clamp(1.0f + (negativeBias * 0.75f) - mitigation, 0.25f, 2.1f);
            return value * scale;
        }

        public float positiveToneIrritation(RumorTone tone) {
            if (tone == null || negativeBias <= 0f) {
                return 0f;
            }
            float base = switch (tone) {
                case BRAG, COZY, WONDER -> 0.04f;
                case WHISPER -> 0.025f;
                default -> 0f;
            };
            if (base <= 0f) {
                return 0f;
            }
            float scaled = base * MathHelper.clamp(negativeBias * 1.35f, 0f, 1.15f);
            float mitigation = base * MathHelper.clamp(positiveBias * 0.5f, 0f, 0.5f);
            float levity = base * MathHelper.clamp(playfulSarcasm * 0.65f, 0f, 0.6f);
            return Math.max(0f, scaled - mitigation - levity);
        }

        public float admirationBonus() {
            if (positiveBias <= 0f) {
                return 0f;
            }
            return MathHelper.clamp(positiveBias * 0.04f, 0f, 0.05f);
        }

        public float sassyAmusement() {
            if (playfulSarcasm <= 0f) {
                return 0f;
            }
            float amusement = (playfulSarcasm * 0.7f) + (positiveBias * 0.2f) - (negativeBias * 0.15f);
            return MathHelper.clamp(amusement, 0f, 1f);
        }
    }
}

