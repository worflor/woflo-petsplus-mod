package woflo.petsplus.state.morality;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Derives behavioral tags from persona axis values.
 * Tags are ephemeral—they emerge when axes align, disappear when they don't.
 */
public final class PersonaBehavioralProfile {
    private final Set<BehavioralTag> activeTags;
    private final MoralityPersona persona;

    public enum BehavioralTag {
        RUTHLESS("Ruthless"),
        NURTURING("Nurturing"),
        OPPORTUNISTIC("Opportunistic"),
        HONORABLE("Honorable"),
        GREEDY("Greedy"),
        MARTYR("Martyr"),
        CAUTIOUS("Cautious"),
        BOLD("Bold"),
        COMPASSIONATE("Compassionate"),
        SELFISH("Selfish"),
        PACK_ANIMAL("Pack Animal"),
        LONE_WOLF("Lone Wolf");

        private final String displayName;

        BehavioralTag(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public PersonaBehavioralProfile(MoralityPersona persona) {
        this.persona = Objects.requireNonNull(persona, "persona");
        this.activeTags = deriveActiveTags();
    }

    private Set<BehavioralTag> deriveActiveTags() {
        Set<BehavioralTag> tags = new HashSet<>();

        if (persona == null) {
            return Collections.emptySet();
        }

        float aggression = persona.aggressionTendency();
        float empathy = persona.empathyLevel();
        float courage = persona.courageBaseline();
        float social = persona.socialOrientation();
        float resources = persona.resourceAttitude();

        // Ruthless: High aggression + Low empathy
        if (aggression > 0.7f && empathy < 0.3f) {
            tags.add(BehavioralTag.RUTHLESS);
        }

        // Nurturing: High empathy + High social
        if (empathy > 0.7f && social > 0.6f) {
            tags.add(BehavioralTag.NURTURING);
        }

        // Opportunistic: Low courage + High aggression (attacks when safe)
        if (courage < 0.3f && aggression > 0.5f) {
            tags.add(BehavioralTag.OPPORTUNISTIC);
        }

        // Honorable: High courage + High aggression (refuses to attack from behind)
        if (courage > 0.7f && aggression > 0.6f) {
            tags.add(BehavioralTag.HONORABLE);
        }

        // Greedy: Low resources + Low social (hoards drops, refuses to share)
        if (resources < 0.3f && social < 0.4f) {
            tags.add(BehavioralTag.GREEDY);
        }

        // Martyr: High empathy + High courage (intercepts damage for owner)
        if (empathy > 0.8f && courage > 0.7f) {
            tags.add(BehavioralTag.MARTYR);
        }

        // Cautious: Low courage (general trait)
        if (courage < 0.4f) {
            tags.add(BehavioralTag.CAUTIOUS);
        }

        // Bold: High courage (general trait)
        if (courage > 0.7f) {
            tags.add(BehavioralTag.BOLD);
        }

        // Compassionate: High empathy (general trait)
        if (empathy > 0.7f) {
            tags.add(BehavioralTag.COMPASSIONATE);
        }

        // Selfish: Low empathy + Low resources
        if (empathy < 0.3f && resources < 0.4f) {
            tags.add(BehavioralTag.SELFISH);
        }

        // Pack Animal: High social
        if (social > 0.7f) {
            tags.add(BehavioralTag.PACK_ANIMAL);
        }

        // Lone Wolf: Low social
        if (social < 0.3f) {
            tags.add(BehavioralTag.LONE_WOLF);
        }

        return tags;
    }

    public Set<BehavioralTag> getActiveTags() {
        return Collections.unmodifiableSet(activeTags);
    }

    public boolean hasTag(BehavioralTag tag) {
        return activeTags.contains(tag);
    }

    /**
     * Calculate "identity dissonance" = how far persona has drifted from neutral baseline.
     * Used for malevolence trigger based on personality shift, not just vice accumulation.
     */
    public float calculateIdentityDissonance() {
        float drift = 0f;
        drift += Math.abs(persona.aggressionTendency() - 0.5f);
        drift += Math.abs(persona.empathyLevel() - 0.5f);
        drift += Math.abs(persona.courageBaseline() - 0.5f);
        drift += Math.abs(persona.socialOrientation() - 0.5f);
        drift += Math.abs(persona.resourceAttitude() - 0.5f);
        return drift / 5.0f; // Normalize to 0-0.5 scale
    }

    @Override
    public String toString() {
        return "PersonaBehavioralProfile{" +
            "tags=" + activeTags +
            ", dissonance=" + calculateIdentityDissonance() +
            '}';
    }
}
