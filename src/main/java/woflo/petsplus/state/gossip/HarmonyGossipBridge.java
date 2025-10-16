package woflo.petsplus.state.gossip;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

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

        return new HarmonyGossipProfile(
            blendBias(setPositiveBias, compatibilityPositive),
            blendBias(setNegativeBias, compatibilityNegative)
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

    public record HarmonyGossipProfile(float positiveBias, float negativeBias) {
        public static final HarmonyGossipProfile NEUTRAL = new HarmonyGossipProfile(0f, 0f);

        public boolean isNeutral() {
            return positiveBias <= 0.0001f && negativeBias <= 0.0001f;
        }

        public float adjustPositive(float value) {
            if (value <= 0f) {
                return 0f;
            }
            float scale = MathHelper.clamp(1.0f + (positiveBias * 0.6f) - (negativeBias * 0.7f), 0.1f, 1.8f);
            return value * scale;
        }

        public float adjustCuriosity(float value) {
            if (value <= 0f) {
                return 0f;
            }
            float scale = MathHelper.clamp(1.0f + (positiveBias * 0.3f) - (negativeBias * 0.5f), 0.1f, 1.6f);
            return value * scale;
        }

        public float adjustStorytellerEcho(float value) {
            if (value <= 0f) {
                return 0f;
            }
            float scale = MathHelper.clamp(1.0f + (positiveBias * 0.5f) - (negativeBias * 0.35f), 0.1f, 1.7f);
            return value * scale;
        }

        public float adjustFrustration(float value) {
            if (value <= 0f) {
                return 0f;
            }
            float scale = MathHelper.clamp(1.0f + (negativeBias * 0.75f) - (positiveBias * 0.2f), 0.3f, 2.2f);
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
            return Math.max(0f, scaled - mitigation);
        }

        public float admirationBonus() {
            if (positiveBias <= 0f) {
                return 0f;
            }
            return MathHelper.clamp(positiveBias * 0.04f, 0f, 0.05f);
        }
    }
}

