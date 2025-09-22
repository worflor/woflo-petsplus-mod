package woflo.petsplus.roles.support;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.stats.PetAttributeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utilities for working with potion items for the Support role.
 */
public final class SupportPotionUtils {
    /**
     * Number of baseline aura pulses a single stored potion provides.
     * Each pulse consumes one charge before perch discounts are applied.
     */
    public static final double BASE_PULSES_PER_POTION = 8.0;
    /**
     * Interval between stored potion aura pulses (7 seconds at 20 ticks per second).
     */
    public static final int SUPPORT_POTION_PULSE_INTERVAL_TICKS = 140;

    private static final int DEFAULT_BASE_POTION_DURATION_TICKS = 3600;
    private static final double MIN_INITIAL_CHARGES = 1.0;
    private static final double EPSILON = 1.0E-4;

    private SupportPotionUtils() {}

    /**
     * Extract potion effects from the given ItemStack suitable for an aura pulse.
     * Returns duration-adjusted copies based on original potion duration + pet level modifiers.
     */
    public static List<StatusEffectInstance> getAuraEffects(ItemStack stack, int petLevel) {
        if (stack == null || stack.isEmpty()) return Collections.emptyList();
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) return Collections.emptyList();

        List<StatusEffectInstance> out = new ArrayList<>();
        for (StatusEffectInstance inst : contents.getEffects()) {
            if (inst == null || inst.getEffectType() == null) continue;

            // Calculate duration: base potion duration + level-based modifier
            int baseDuration = inst.getDuration();
            int levelBonus = petLevel * 20; // 1 second (20 ticks) per pet level
            int finalDuration = Math.max(60, baseDuration + levelBonus); // Minimum 3 seconds

            // Recreate with calculated duration, preserve amplifier
            out.add(new StatusEffectInstance(inst.getEffectType(), finalDuration, inst.getAmplifier(), false, true, true));
        }
        return out;
    }

    /**
     * Get the base duration (in ticks) of the supplied potion stack.
     * Falls back to the default 3 minute duration when no effects are present.
     */
    public static int getBasePotionDurationTicks(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return DEFAULT_BASE_POTION_DURATION_TICKS;
        }

        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null) {
            return DEFAULT_BASE_POTION_DURATION_TICKS;
        }

        int maxDuration = 0;
        for (StatusEffectInstance inst : contents.getEffects()) {
            if (inst == null) continue;
            maxDuration = Math.max(maxDuration, inst.getDuration());
        }

        return Math.max(20, maxDuration <= 0 ? DEFAULT_BASE_POTION_DURATION_TICKS : maxDuration);
    }

    /**
     * Get the effective aura duration for storing in component state.
     * This should be the pulse duration that gets applied each tick.
     */
    public static int getAuraPulseDuration(int basePotionDuration, int petLevel) {
        int sanitizedDuration = Math.max(20, basePotionDuration);
        int levelBonus = petLevel * 10; // 0.5 seconds (10 ticks) per level for aura pulses
        return Math.max(40, Math.min(sanitizedDuration / 3, 80) + levelBonus); // Between 2-4+ seconds
    }

    /**
     * Get the initial number of aura charges granted when a potion is stored.
     */
    public static double getInitialChargePool(ItemStack stack, PetComponent component) {
        int baseDuration = getBasePotionDurationTicks(stack);
        return getInitialChargePoolFromDuration(baseDuration, component);
    }

    /**
     * Recalculate the aura charge pool from stored component state.
     */
    public static double recalculateChargePoolFromState(PetComponent component) {
        Integer storedDuration = component.getStateData("support_potion_source_duration", Integer.class);
        int baseDuration = storedDuration != null && storedDuration > 0
            ? storedDuration
            : DEFAULT_BASE_POTION_DURATION_TICKS;
        return getInitialChargePoolFromDuration(baseDuration, component);
    }

    /**
     * Get the current aura efficiency multiplier (1.0 = baseline).
     */
    public static double getAuraEfficiencyMultiplier(PetComponent component) {
        if (component == null) {
            return 1.0;
        }
        float scalar = PetAttributeManager.getEffectiveScalar(
            "aura",
            component.getRole(),
            component.getCharacteristics(),
            component.getLevel()
        );
        return Math.max(0.1, 1.0 + scalar);
    }

    private static double getInitialChargePoolFromDuration(int basePotionDuration, PetComponent component) {
        int sanitizedDuration = Math.max(20, basePotionDuration);
        double durationScale = sanitizedDuration / (double) DEFAULT_BASE_POTION_DURATION_TICKS;
        double baseCharges = BASE_PULSES_PER_POTION * durationScale;
        double efficiencyMultiplier = getAuraEfficiencyMultiplier(component);
        double totalCharges = baseCharges * efficiencyMultiplier;
        return Math.max(MIN_INITIAL_CHARGES, totalCharges);
    }

    /**
     * Lightweight snapshot of stored potion data for reuse across helpers.
     */
    public record SupportPotionState(List<String> serializedEffects, int auraDurationTicks, ChargeState chargeState) {
        public SupportPotionState {
            serializedEffects = serializedEffects == null ? List.of() : List.copyOf(serializedEffects);
            auraDurationTicks = Math.max(0, auraDurationTicks);
            chargeState = chargeState == null ? ChargeState.EMPTY : chargeState;
        }

        public boolean isValid() {
            return !serializedEffects.isEmpty() && chargeState.hasCharges();
        }

        public SupportPotionState withChargeState(ChargeState newChargeState) {
            return new SupportPotionState(serializedEffects, auraDurationTicks, newChargeState);
        }
    }

    /**
     * Tracks total/remaining charges and source metadata for a stored potion.
     */
    public record ChargeState(double total, double remaining, double efficiencySnapshot, int sourceDurationTicks) {
        public static final ChargeState EMPTY = new ChargeState(0.0, 0.0, 0.0, 0);

        public ChargeState {
            total = Math.max(0.0, total);
            remaining = Math.max(0.0, remaining);
            efficiencySnapshot = Math.max(0.0, efficiencySnapshot);
            sourceDurationTicks = Math.max(0, sourceDurationTicks);
        }

        public ChargeState consume(double amount) {
            double sanitizedAmount = Math.max(0.0, amount);
            double updatedRemaining = Math.max(0.0, remaining - sanitizedAmount);
            return new ChargeState(total, updatedRemaining, efficiencySnapshot, sourceDurationTicks);
        }

        public boolean hasCharges() {
            return total > EPSILON && remaining > EPSILON;
        }
    }

    private static final SupportPotionState EMPTY_STATE = new SupportPotionState(Collections.emptyList(), 0, ChargeState.EMPTY);

    /**
     * Check if the component currently holds a stored potion.
     */
    public static boolean hasStoredPotion(PetComponent component) {
        return component != null && Boolean.TRUE.equals(component.getStateData("support_potion_present", Boolean.class));
    }

    /**
     * Build a stored-potion snapshot from an item stack for persistence.
     */
    public static SupportPotionState createStateFromStack(ItemStack stack, PetComponent component) {
        if (component == null || stack == null || stack.isEmpty()) {
            return EMPTY_STATE;
        }

        List<StatusEffectInstance> effects = getAuraEffects(stack, component.getLevel());
        if (effects.isEmpty()) {
            return EMPTY_STATE;
        }

        int baseDuration = getBasePotionDurationTicks(stack);
        int auraPulseDuration = getAuraPulseDuration(baseDuration, component.getLevel());
        double efficiency = getAuraEfficiencyMultiplier(component);
        double charges = getInitialChargePoolFromDuration(baseDuration, component);

        ChargeState chargeState = new ChargeState(charges, charges, efficiency, baseDuration);
        return new SupportPotionState(serializeEffects(effects), auraPulseDuration, chargeState);
    }

    /**
     * Read the stored potion snapshot from the component, sanitizing persisted values.
     */
    public static SupportPotionState getStoredState(PetComponent component) {
        if (component == null) {
            return EMPTY_STATE;
        }

        SupportPotionState state = readStoredStateInternal(component);
        if (!state.isValid()) {
            clearStoredPotion(component);
            return EMPTY_STATE;
        }

        writeStoredState(component, state);
        return state;
    }

    /**
     * Persist a stored potion snapshot back onto the component.
     */
    public static void writeStoredState(PetComponent component, SupportPotionState state) {
        if (component == null) {
            return;
        }

        boolean valid = state != null && state.isValid();
        SupportPotionState toStore = valid ? state : EMPTY_STATE;
        ChargeState charges = toStore.chargeState();

        component.setStateData("support_potion_present", valid);
        component.setStateData("support_potion_effects", toStore.serializedEffects());
        component.setStateData("support_potion_aura_duration", toStore.auraDurationTicks());
        component.setStateData("support_potion_total_charges", charges.total());
        component.setStateData("support_potion_charges_remaining", Math.max(0.0, charges.remaining()));
        component.setStateData("support_potion_source_duration", charges.sourceDurationTicks());
        component.setStateData("support_potion_efficiency_multiplier", charges.efficiencySnapshot());
    }

    /**
     * Consume a number of charges from the stored potion and persist the result.
     */
    public static void consumeCharges(PetComponent component, SupportPotionState state, double amount) {
        if (component == null || state == null || !state.isValid()) {
            return;
        }

        ChargeState updated = state.chargeState().consume(amount);
        if (!updated.hasCharges()) {
            clearStoredPotion(component);
        } else {
            writeStoredState(component, state.withChargeState(updated));
        }
    }

    /**
     * Convert serialized effect entries back into status effects for aura pulses.
     */
    public static List<StatusEffectInstance> deserializeEffects(List<String> serialized, int auraDurationTicks) {
        if (serialized == null || serialized.isEmpty()) {
            return Collections.emptyList();
        }

        int sanitizedDuration = Math.max(20, auraDurationTicks);
        List<StatusEffectInstance> effects = new ArrayList<>();
        for (String entry : serialized) {
            if (entry == null || entry.isEmpty()) continue;
            String[] parts = entry.split("\\|");
            if (parts.length != 2) continue;

            Identifier id = Identifier.tryParse(parts[0]);
            if (id == null) continue;
            int amplifier;
            try {
                amplifier = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ex) {
                continue;
            }

            var effectEntry = Registries.STATUS_EFFECT.getEntry(id);
            if (effectEntry.isEmpty()) continue;
            effects.add(new StatusEffectInstance(effectEntry.get(), sanitizedDuration, Math.max(0, amplifier), false, true, true));
        }

        return effects;
    }

    /**
     * Determine how many charges a single pulse should consume given current stance/perch bonuses.
     */
    public static double getConsumptionPerPulse(PetComponent component) {
        double consumption = 1.0;
        if (component != null && component.isPerched()) {
            double discount = PetsPlusConfig.getInstance().getDouble("support", "perchSipDiscount", 0.20);
            double multiplier = 1.0 - discount;
            if (multiplier < 0.0) multiplier = 0.0;
            if (multiplier > 1.0) multiplier = 1.0;
            consumption *= multiplier;
        }
        return consumption;
    }

    /**
     * Clear any stored potion data from the supplied component.
     */
    public static void clearStoredPotion(PetComponent component) {
        if (component == null) {
            return;
        }
        writeStoredState(component, EMPTY_STATE);
    }

    private static SupportPotionState readStoredStateInternal(PetComponent component) {
        @SuppressWarnings("unchecked")
        List<String> serialized = component.getStateData("support_potion_effects", List.class);
        if (serialized == null) {
            serialized = Collections.emptyList();
        }

        Integer auraDuration = component.getStateData("support_potion_aura_duration", Integer.class);
        int sanitizedAuraDuration = auraDuration != null ? Math.max(20, auraDuration) : 80;

        Integer sourceDuration = component.getStateData("support_potion_source_duration", Integer.class);
        int sanitizedSourceDuration = (sourceDuration != null && sourceDuration > 0)
            ? sourceDuration
            : DEFAULT_BASE_POTION_DURATION_TICKS;

        Double total = component.getStateData("support_potion_total_charges", Double.class);
        Double remaining = component.getStateData("support_potion_charges_remaining", Double.class);
        Double storedEfficiency = component.getStateData("support_potion_efficiency_multiplier", Double.class);
        double currentEfficiency = getAuraEfficiencyMultiplier(component);

        double totalCharges = (total == null || total <= 0.0)
            ? getInitialChargePoolFromDuration(sanitizedSourceDuration, component)
            : total;

        double chargesRemaining = remaining == null ? totalCharges : Math.min(remaining, totalCharges);
        double efficiencySnapshot = storedEfficiency != null ? storedEfficiency : currentEfficiency;

        if (Math.abs(currentEfficiency - efficiencySnapshot) > EPSILON) {
            double recalculated = getInitialChargePoolFromDuration(sanitizedSourceDuration, component);
            double ratio = totalCharges > EPSILON ? recalculated / totalCharges : 1.0;
            chargesRemaining *= ratio;
            totalCharges = recalculated;
            efficiencySnapshot = currentEfficiency;
        }

        chargesRemaining = Math.max(0.0, Math.min(chargesRemaining, totalCharges));
        ChargeState charges = new ChargeState(totalCharges, chargesRemaining, efficiencySnapshot, sanitizedSourceDuration);

        return new SupportPotionState(serialized, sanitizedAuraDuration, charges);
    }

    private static List<String> serializeEffects(List<StatusEffectInstance> effects) {
        if (effects == null || effects.isEmpty()) {
            return List.of();
        }

        List<String> serialized = new ArrayList<>(effects.size());
        for (StatusEffectInstance effect : effects) {
            if (effect == null || effect.getEffectType() == null) continue;
            Identifier id = Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
            if (id == null) continue;
            serialized.add(id.toString() + "|" + Math.max(0, effect.getAmplifier()));
        }
        return serialized.isEmpty() ? List.of() : List.copyOf(serialized);
    }
}
