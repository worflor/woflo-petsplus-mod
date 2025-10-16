package woflo.petsplus.state.gossip;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        if (storytellerState == null || listenerState == null) {
            return HarmonyGossipProfile.NEUTRAL;
        }
        float positiveBias = computeSharedBias(storytellerState.harmonySetIds(), listenerState.harmonySetIds(),
            storytellerState.harmonyStrength(), listenerState.harmonyStrength());
        float negativeBias = computeSharedBias(storytellerState.disharmonySetIds(), listenerState.disharmonySetIds(),
            storytellerState.disharmonyStrength(), listenerState.disharmonyStrength());
        return new HarmonyGossipProfile(positiveBias, negativeBias);
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

