package woflo.petsplus.state.morality;

import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

/**
 * Simple accessor for pet persona axes.
 * 
 * Used by combat, emotions, and AI systems to apply persona-based behavior modulation.
 * Returns 0.5f (neutral) as fallback if persona unavailable.
 */
public final class PersonaHelper {
    private PersonaHelper() {}

    /**
     * Get persona from pet, or null if unavailable.
     */
    @Nullable
    public static MoralityPersona getPersona(PetComponent pc) {
        if (pc == null) {
            return null;
        }
        MalevolenceLedger ledger = pc.getMalevolenceLedger();
        if (ledger == null) {
            return null;
        }
        return ledger.getPersona();
    }

    /**
     * Get aggression axis (0.0 = pacifist, 1.0 = bloodthirsty).
     * Returns 0.5f if unavailable.
     */
    public static float getAggression(PetComponent pc) {
        MoralityPersona persona = getPersona(pc);
        return persona != null ? persona.aggressionTendency() : 0.5f;
    }

    /**
     * Get empathy axis (0.0 = callous, 1.0 = compassionate).
     * Returns 0.5f if unavailable.
     */
    public static float getEmpathy(PetComponent pc) {
        MoralityPersona persona = getPersona(pc);
        return persona != null ? persona.empathyLevel() : 0.5f;
    }

    /**
     * Get courage axis (0.0 = cowardly, 1.0 = brave).
     * Returns 0.5f if unavailable.
     */
    public static float getCourage(PetComponent pc) {
        MoralityPersona persona = getPersona(pc);
        return persona != null ? persona.courageBaseline() : 0.5f;
    }

    /**
     * Get social axis (0.0 = solitary, 1.0 = pack-focused).
     * Returns 0.5f if unavailable.
     */
    public static float getSocial(PetComponent pc) {
        MoralityPersona persona = getPersona(pc);
        return persona != null ? persona.socialOrientation() : 0.5f;
    }

    /**
     * Get resource axis (0.0 = selfish, 1.0 = generous).
     * Returns 0.5f if unavailable.
     */
    public static float getResource(PetComponent pc) {
        MoralityPersona persona = getPersona(pc);
        return persona != null ? persona.resourceAttitude() : 0.5f;
    }
}
