package woflo.petsplus.state.relationships;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationshipProfileTest {

    @Test
    void recalculateAccumulatesWeightedInteractionDeltas() {
        UUID entityId = UUID.randomUUID();
        long baseTick = 1_000L;
        RelationshipProfile profile = RelationshipProfile.createNew(entityId, baseTick);

        InteractionType interaction = InteractionType.FEEDING;
        InteractionType.DimensionalResult deltas = new InteractionType.DimensionalResult(
            interaction.getTrustDelta(),
            interaction.getAffectionDelta(),
            interaction.getRespectDelta()
        );

        long[] interactionTicks = new long[] {
            baseTick,
            baseTick + 20,
            baseTick + 40,
            baseTick + 60,
            baseTick + 80
        };

        for (long tick : interactionTicks) {
            profile = profile.recordInteraction(interaction, deltas, tick);
        }

        long recalculationTick = baseTick + 160;
        RelationshipProfile recalculated = profile.recalculate(recalculationTick);

        float expectedTrust = 0.0f;
        float expectedAffection = 0.0f;
        float expectedRespect = 0.0f;

        for (long interactionTick : interactionTicks) {
            float weight = (float) Math.exp(-(recalculationTick - interactionTick) / 144000.0);
            expectedTrust += deltas.trustDelta() * weight;
            expectedAffection += deltas.affectionDelta() * weight;
            expectedRespect += deltas.respectDelta() * weight;
        }

        assertEquals(expectedTrust, recalculated.trust(), 1.0e-3f);
        assertEquals(expectedAffection, recalculated.affection(), 1.0e-3f);
        assertEquals(expectedRespect, recalculated.respect(), 1.0e-3f);

        assertTrue(recalculated.trust() > 0.35f, "trust should remain near the accumulated contributions");
        assertTrue(recalculated.trust() <= 1.0f);
        assertTrue(recalculated.affection() <= 1.0f);
        assertTrue(recalculated.respect() <= 1.0f);
    }
}

