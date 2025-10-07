package woflo.petsplus.events;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.relationships.InteractionType;

import java.util.UUID;

/**
 * Detects and records entity interactions that build/damage relationships.
 * Hooks into existing event systems to track:
 * - Feeding
 * - Petting (via PettingHandler)
 * - Combat assistance
 * - Attacks/threats
 * - Rescues and healing
 * - Proximity (passive)
 */
public class RelationshipEventHandler {
    
    /**
     * Record feeding interaction.
     * Called when any entity feeds a pet.
     */
    public static void onPetFed(MobEntity pet, LivingEntity feeder) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || feeder == null) {
            return;
        }
        
        UUID feederUuid = feeder.getUuid();
        if (feederUuid == null) {
            return;
        }
        
        // Apply species-specific multipliers
        float trustMult = 1.0f;
        float affectionMult = 1.0f;
        float respectMult = 1.0f;
        
        // Hungry pets value feeding more
        if (pet.getHealth() < pet.getMaxHealth() * 0.5f) {
            trustMult = 1.3f;
            affectionMult = 1.4f;
        }
        
        pc.recordEntityInteraction(
            feederUuid,
            InteractionType.FEEDING,
            trustMult,
            affectionMult,
            respectMult
        );
    }
    
    /**
     * Record petting interaction.
     * Called from PettingHandler when entity pets the pet.
     */
    public static void onPetPetted(MobEntity pet, LivingEntity petter) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || petter == null) {
            return;
        }
        
        UUID petterUuid = petter.getUuid();
        if (petterUuid == null) {
            return;
        }
        
        // Check if pet is comfortable with petting
        float existingComfort = pc.getComfortWith(petterUuid);
        
        float trustMult = 1.0f;
        float affectionMult = 1.0f;
        float respectMult = 1.0f;
        
        // Uncomfortable petting (stranger) is less effective
        if (existingComfort < 0.3f) {
            trustMult = 0.6f;
            affectionMult = 0.7f;
        } else if (existingComfort > 0.7f) {
            // Comfortable petting is more meaningful
            affectionMult = 1.3f;
        }
        
        pc.recordEntityInteraction(
            petterUuid,
            InteractionType.PETTING,
            trustMult,
            affectionMult,
            respectMult
        );
    }
    
    /**
     * Record combat ally interaction.
     * Called when entity and pet fight the same target.
     */
    public static void onCombatAlly(MobEntity pet, LivingEntity ally, LivingEntity commonTarget) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || ally == null) {
            return;
        }
        
        UUID allyUuid = ally.getUuid();
        if (allyUuid == null) {
            return;
        }
        
        float trustMult = 1.0f;
        float affectionMult = 1.0f;
        float respectMult = 1.2f; // Combat shows competence
        
        // Dangerous combat = stronger bond
        if (commonTarget instanceof MobEntity target) {
            if (target.getMaxHealth() > pet.getMaxHealth() * 1.5f) {
                trustMult = 1.4f;
                affectionMult = 1.2f;
                respectMult = 1.5f; // Impressive ally
            }
        }
        
        pc.recordEntityInteraction(
            allyUuid,
            InteractionType.COMBAT_ALLY,
            trustMult,
            affectionMult,
            respectMult
        );
    }
    
    /**
     * Record attack interaction.
     * Called when entity attacks the pet.
     */
    public static void onPetAttacked(MobEntity pet, LivingEntity attacker, float damage) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || attacker == null) {
            return;
        }
        
        UUID attackerUuid = attacker.getUuid();
        if (attackerUuid == null) {
            return;
        }
        
        float trustMult = 1.0f;
        float affectionMult = 1.0f;
        float respectMult = 1.0f;
        
        // Severe damage = more fear/respect
        if (damage > pet.getMaxHealth() * 0.3f) {
            trustMult = 1.5f; // More fear
            affectionMult = 1.3f;
            respectMult = 1.4f; // Dangerous = respect
        }
        
        pc.recordEntityInteraction(
            attackerUuid,
            InteractionType.ATTACK,
            trustMult,
            affectionMult,
            respectMult
        );
    }
    
    /**
     * Alias for onCombatAlly - tracks pet fighting alongside an ally.
     */
    public static void onPetCombatAlly(MobEntity pet, LivingEntity ally, LivingEntity commonTarget) {
        onCombatAlly(pet, ally, commonTarget);
    }
    
    /**
     * Record owner attack interaction.
     * Called when entity attacks the pet's owner.
     */
    public static void onOwnerAttacked(MobEntity pet, LivingEntity attacker) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || attacker == null) {
            return;
        }
        
        UUID attackerUuid = attacker.getUuid();
        if (attackerUuid == null) {
            return;
        }
        
        // Attacking owner is maximum betrayal
        pc.recordEntityInteraction(
            attackerUuid,
            InteractionType.OWNER_ATTACK,
            1.5f,  // Massive trust loss
            1.2f,  // Affection loss
            1.0f   // Respect (dangerous enemy)
        );
    }
    
    /**
     * Record defense interaction.
     * Called when entity defends pet from attacker.
     */
    public static void onPetDefended(MobEntity pet, LivingEntity defender, LivingEntity threat) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || defender == null) {
            return;
        }
        
        UUID defenderUuid = defender.getUuid();
        if (defenderUuid == null) {
            return;
        }
        
        float trustMult = 1.3f; // Protector = trust
        float affectionMult = 1.2f;
        float respectMult = 1.4f; // Capable defender
        
        pc.recordEntityInteraction(
            defenderUuid,
            InteractionType.DEFENDED,
            trustMult,
            affectionMult,
            respectMult
        );
    }
    
    /**
     * Record taming interaction.
     * Called when entity successfully tames a pet - establishes initial relationship.
     */
    public static void onPetTamed(MobEntity pet, PlayerEntity tamer) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || tamer == null) {
            return;
        }
        
        UUID tamerUuid = tamer.getUuid();
        if (tamerUuid == null) {
            return;
        }
        
        // Taming establishes strong initial familiarity, trust, and affection
        pc.recordEntityInteraction(
            tamerUuid,
            InteractionType.FEEDING,  // Closest existing type
            2.0f,  // Strong initial trust
            1.8f,  // Strong initial affection
            1.5f   // Respect for capability
        );
    }
    
    /**
     * Record attack from non-owner player.
     * Called when a different player (not owner) attacks the pet.
     */
    public static void onPetAttackedByOther(MobEntity pet, PlayerEntity attacker, float damage) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || attacker == null) {
            return;
        }
        
        UUID attackerUuid = attacker.getUuid();
        if (attackerUuid == null) {
            return;
        }
        
        // Being attacked by another player reduces trust and affection
        float intensity = Math.min(1.5f, 0.8f + (damage / 10.0f));
        
        pc.recordEntityInteraction(
            attackerUuid,
            InteractionType.ATTACK,
            -intensity * 0.8f,  // Major trust loss
            -intensity * 0.6f,  // Affection loss
            intensity * 1.2f    // Respect for power/threat
        );
    }
    
    /**
     * Record defense interaction when pet protects owner.
     * Called when pet redirects/absorbs damage meant for owner.
     */
    public static void onPetDefendedOwner(MobEntity pet, PlayerEntity owner, float damageAbsorbed) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || owner == null) {
            return;
        }
        
        UUID ownerUuid = owner.getUuid();
        if (ownerUuid == null) {
            return;
        }
        
        // Defending owner builds trust and affection
        float intensity = Math.min(2.0f, 1.0f + (damageAbsorbed / 10.0f));
        
        pc.recordEntityInteraction(
            ownerUuid,
            InteractionType.DEFENDED,
            intensity * 1.2f,  // Trust grows from protecting
            intensity * 1.1f,  // Affection grows from bonding
            intensity * 0.9f   // Slightly reduces authority perception
        );
    }
    
    /**
     * Record rescue interaction.
     * Called when entity rescues pet from danger (fire, drowning, void, etc).
     */
    public static void onPetRescued(MobEntity pet, LivingEntity rescuer) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || rescuer == null) {
            return;
        }
        
        UUID rescuerUuid = rescuer.getUuid();
        if (rescuerUuid == null) {
            return;
        }
        
        // Rescue is a powerful bonding moment
        pc.recordEntityInteraction(
            rescuerUuid,
            InteractionType.RESCUE,
            1.8f,  // Massive trust gain
            1.5f,  // Strong affection
            1.3f   // Competent rescuer
        );
    }
    
    /**
     * Record healing interaction.
     * Called when entity heals the pet (potions, abilities, etc).
     */
    public static void onPetHealed(MobEntity pet, LivingEntity healer, float healAmount) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || healer == null) {
            return;
        }
        
        UUID healerUuid = healer.getUuid();
        if (healerUuid == null) {
            return;
        }
        
        float trustMult = 1.0f;
        float affectionMult = 1.0f;
        float respectMult = 1.0f;
        
        // Healing from near-death is more impactful
        if (pet.getHealth() < pet.getMaxHealth() * 0.3f) {
            trustMult = 1.4f;
            affectionMult = 1.3f;
        }
        
        pc.recordEntityInteraction(
            healerUuid,
            InteractionType.HEALING,
            trustMult,
            affectionMult,
            respectMult
        );
    }
    
    /**
     * Record play interaction.
     * Called when entity plays with pet (throws item, toy interaction, etc).
     */
    public static void onPlayInteraction(MobEntity pet, LivingEntity player) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || player == null) {
            return;
        }
        
        UUID playerUuid = player.getUuid();
        if (playerUuid == null) {
            return;
        }
        
        // Check pet mood for play receptiveness
        float affectionMult = 1.0f;
        if (pc.getCurrentMood() == PetComponent.Mood.PLAYFUL) {
            affectionMult = 1.5f; // Loves playing when playful
        }
        
        pc.recordEntityInteraction(
            playerUuid,
            InteractionType.PLAY,
            1.0f,
            affectionMult,
            0.9f  // Play reduces authority perception
        );
    }
    
    /**
     * Record gentle approach interaction.
     * Called when entity approaches slowly while crouching.
     */
    public static void onGentleApproach(MobEntity pet, LivingEntity approacher) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || approacher == null) {
            return;
        }
        
        UUID approacherUuid = approacher.getUuid();
        if (approacherUuid == null) {
            return;
        }
        
        // Gentle approach to stranger is more impactful
        float existingTrust = pc.getTrustWith(approacherUuid);
        
        float trustMult = 1.0f;
        if (existingTrust < 0.2f) {
            trustMult = 1.3f; // Builds trust with cautious strangers
        }
        
        pc.recordEntityInteraction(
            approacherUuid,
            InteractionType.GENTLE_APPROACH,
            trustMult,
            1.0f,
            1.0f
        );
    }
    
    /**
     * Record gift interaction.
     * Called when entity gives pet a valuable item.
     */
    public static void onGiftGiven(MobEntity pet, LivingEntity giver, float giftValue) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || giver == null) {
            return;
        }
        
        UUID giverUuid = giver.getUuid();
        if (giverUuid == null) {
            return;
        }
        
        // More valuable gifts = stronger impact
        float valueMult = Math.min(1.5f, 0.8f + (giftValue * 0.2f));
        
        pc.recordEntityInteraction(
            giverUuid,
            InteractionType.GIFT,
            valueMult,
            valueMult * 1.2f,  // Affection scales more
            valueMult * 0.8f
        );
    }
    
    /**
     * Record when owner attacked their own pet.
     */
    public static void onOwnerAttackedPet(MobEntity pet, PlayerEntity owner, float damage) {
        onPetAttacked(pet, owner, damage);
    }
    
    /**
     * Record when pet attacked owner (accidental or berserked).
     */
    public static void onPetAttackedOwner(MobEntity pet, PlayerEntity owner, float damage) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || owner == null) {
            return;
        }
        
        UUID ownerUuid = owner.getUuid();
        if (ownerUuid == null) {
            return;
        }
        
        // Attacking owner is serious - major relationship damage
        pc.recordEntityInteraction(
            ownerUuid,
            InteractionType.ATTACK,
            1.8f,  // Major trust loss
            1.5f,  // Affection loss
            1.0f   // Respect unchanged
        );
    }
    
    /**
     * Record when pet attacked ally pet (same owner).
     */
    public static void onPetAttackedAlly(MobEntity attacker, MobEntity victim) {
        PetComponent attackerPc = PetComponent.get(attacker);
        if (attackerPc == null || victim == null) {
            return;
        }
        
        UUID victimUuid = victim.getUuid();
        if (victimUuid == null) {
            return;
        }
        
        // Attacking ally creates tension
        attackerPc.recordEntityInteraction(
            victimUuid,
            InteractionType.ATTACK,
            0.8f,  // Moderate trust loss
            0.9f,  // Affection loss
            1.0f   // Respect unchanged
        );
    }
    
    /**
     * Record cross-owner pet rivalry.
     */
    public static void onPetAttackedByRivalPet(MobEntity pet, MobEntity attacker, float damage) {
        onPetAttacked(pet, attacker, damage);
    }
    
    /**
     * Record cross-owner pet rivalry.
     * Called when pet attacks another owner's pet (not ally, different owner).
     */
    public static void onPetAttackedRivalPet(MobEntity attacker, MobEntity victim, float damage) {
        PetComponent attackerPc = PetComponent.get(attacker);
        PetComponent victimPc = PetComponent.get(victim);
        
        if (attackerPc == null || victimPc == null) {
            return;
        }
        
        UUID victimUuid = victim.getUuid();
        UUID attackerUuid = attacker.getUuid();
        
        if (victimUuid == null || attackerUuid == null) {
            return;
        }
        
        // Attacker gains confidence/dominance toward victim
        float intensity = Math.min(1.2f, 0.6f + (damage / 15.0f));
        attackerPc.recordEntityInteraction(
            victimUuid,
            InteractionType.ATTACK,
            -0.2f * intensity,  // Trust loss (rivals)
            -0.3f * intensity,  // Affection loss
            intensity * 1.1f    // Respect for opponent strength
        );
        
        // Victim learns fear/caution toward attacker
        victimPc.recordEntityInteraction(
            attackerUuid,
            InteractionType.ATTACK,
            -intensity * 0.6f,  // Major trust loss
            -intensity * 0.4f,  // Affection loss
            intensity * 1.3f    // High respect/fear for attacker
        );
    }
    
    /**
     * Record threat interaction.
     * Called when entity threatens pet (rapid approach, weapon drawn, etc).
     */
    public static void onThreatDetected(MobEntity pet, LivingEntity threat) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || threat == null) {
            return;
        }
        
        UUID threatUuid = threat.getUuid();
        if (threatUuid == null) {
            return;
        }
        
        pc.recordEntityInteraction(
            threatUuid,
            InteractionType.THREAT,
            1.0f,
            1.0f,
            1.2f  // Threatening = shows power
        );
    }
    
    // ========== Species Memory Methods ==========
    
    /**
     * Record when pet kills a wild animal.
     */
    public static void onPetKilledWildAnimal(MobEntity pet, LivingEntity wildAnimal) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || wildAnimal == null) {
            return;
        }
        
        net.minecraft.entity.EntityType<?> species = wildAnimal.getType();
        float targetStrength = wildAnimal.getMaxHealth() / Math.max(1.0f, pet.getMaxHealth());
        
        pc.recordSpeciesInteraction(species, 
            woflo.petsplus.state.relationships.SpeciesMemory.InteractionContext.petKilledWild(targetStrength));
    }
    
    /**
     * Record when pet is attacked by a wild animal.
     */
    public static void onPetAttackedByWildAnimal(MobEntity pet, LivingEntity wildAnimal, float damage) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || wildAnimal == null) {
            return;
        }
        
        net.minecraft.entity.EntityType<?> species = wildAnimal.getType();
        float damageIntensity = damage / Math.max(1.0f, pet.getMaxHealth());
        float targetStrength = wildAnimal.getMaxHealth() / Math.max(1.0f, pet.getMaxHealth());
        
        pc.recordSpeciesInteraction(species,
            woflo.petsplus.state.relationships.SpeciesMemory.InteractionContext.petAttackedByWild(damageIntensity, targetStrength));
    }
    
    /**
     * Record when pet is killed by a wild animal (called before death).
     */
    public static void onPetKilledByWildAnimal(MobEntity pet, LivingEntity wildAnimal) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || wildAnimal == null) {
            return;
        }
        
        net.minecraft.entity.EntityType<?> species = wildAnimal.getType();
        float targetStrength = wildAnimal.getMaxHealth() / Math.max(1.0f, pet.getMaxHealth());
        
        pc.recordSpeciesInteraction(species,
            woflo.petsplus.state.relationships.SpeciesMemory.InteractionContext.petKilledByWild(targetStrength));
    }
    
    /**
     * Record when pet observes owner hunting/killing a wild animal.
     */
    public static void onPetObservedOwnerHunt(MobEntity pet, LivingEntity wildAnimal) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || wildAnimal == null) {
            return;
        }
        
        net.minecraft.entity.EntityType<?> species = wildAnimal.getType();
        float targetStrength = wildAnimal.getMaxHealth() / Math.max(1.0f, pet.getMaxHealth());
        
        pc.recordSpeciesInteraction(species,
            woflo.petsplus.state.relationships.SpeciesMemory.InteractionContext.observedOwnerHunt(targetStrength));
    }
    
    /**
     * Record when pet observes owner feeding/breeding a wild animal.
     */
    public static void onPetObservedOwnerFeed(MobEntity pet, LivingEntity wildAnimal) {
        PetComponent pc = PetComponent.get(pet);
        if (pc == null || wildAnimal == null) {
            return;
        }
        
        net.minecraft.entity.EntityType<?> species = wildAnimal.getType();
        
        pc.recordSpeciesInteraction(species,
            woflo.petsplus.state.relationships.SpeciesMemory.InteractionContext.observedOwnerFeed());
    }
}
