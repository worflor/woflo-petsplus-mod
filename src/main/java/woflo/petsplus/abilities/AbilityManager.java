package woflo.petsplus.abilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Ability;
import woflo.petsplus.api.DamageInterceptionResult;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.event.AbilityActivationEvent;
import woflo.petsplus.api.registry.AbilityType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.modules.ProgressionModule;
import woflo.petsplus.state.processing.OwnerBatchSnapshot;

/**
 * Manages all abilities and their activation for pets.
 */
public class AbilityManager {
    private static final String EVENT_DAMAGE = "damage";
    private static final String EVENT_DAMAGE_SOURCE = "damage_source";
    private static final String EVENT_LETHAL_DAMAGE = "lethal_damage";
    private static final String EVENT_INTERCEPT_DAMAGE = "intercept_damage";
    private static final String EVENT_DAMAGE_RESULT = "damage_result";
    private static final Map<Identifier, Ability> ALL_ABILITIES = new HashMap<>();
    private static final Map<Identifier, AbilityDispatchEntry> DISPATCH_ENTRIES = new HashMap<>();

    /**
     * Initialize the ability system with default abilities.
     */
    public static void initialize() {
        reloadFromRegistry();
    }

    /**
     * Rebuilds cached ability instances from the active registry contents.
     */
    public static synchronized void reloadFromRegistry() {
        ALL_ABILITIES.clear();
        DISPATCH_ENTRIES.clear();

        int nullInstances = 0;
        for (AbilityType type : PetsPlusRegistries.abilityTypeRegistry()) {
            Ability ability;
            try {
                ability = type.createAbility();
            } catch (Exception e) {
                Petsplus.LOGGER.error("Failed to instantiate ability {}", type.id(), e);
                nullInstances++;
                continue;
            }

            if (ability == null) {
                Petsplus.LOGGER.warn("Registry ability {} returned null instance", type.id());
                nullInstances++;
                continue;
            }

            Identifier abilityId = ability.getId();
            if (abilityId == null) {
                abilityId = type.id();
            }

            ALL_ABILITIES.put(abilityId, ability);
            DISPATCH_ENTRIES.put(abilityId, AbilityDispatchEntry.from(ability, resolveEventKey(ability)));
        }

        if (ALL_ABILITIES.isEmpty()) {
            Petsplus.LOGGER.debug("Ability registry reload completed with no abilities available.");
        } else {
            Petsplus.LOGGER.info("Loaded {} abilities ({} skipped)", ALL_ABILITIES.size(), nullInstances);
        }
    }

    /**
     * Trigger abilities for a pet based on the given context.
     */
    public static AbilityTriggerResult triggerAbilities(MobEntity pet, TriggerContext context) {
        if (pet == null) {
            return AbilityTriggerResult.empty();
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null) {
            return AbilityTriggerResult.empty();
        }

        List<CompiledAbility> compiledAbilities = resolveComponentAbilities(component, context.getEventType());
        if (compiledAbilities.isEmpty()) {
            return AbilityTriggerResult.empty();
        }

        TriggerContext petContext = cloneContextForPet(pet, context);
        return executeCompiledAbilities(component, compiledAbilities, petContext);
    }

    /**
     * Triggers a compiled ability batch for an owner-scoped event by grouping pets by
     * role and reusing compiled ability metadata for the entire swarm.
     */
    public static AbilityTriggerResult triggerAbilitiesForOwnerEvent(ServerWorld world,
                                                                     @Nullable ServerPlayerEntity ownerHint,
                                                                     List<PetComponent> pets,
                                                                     String triggerId,
                                                                     Map<String, Object> eventData) {
        if (world == null || triggerId == null) {
            return AbilityTriggerResult.empty();
        }
        String trimmedTrigger = triggerId.trim();
        if (trimmedTrigger.isEmpty() || pets == null || pets.isEmpty()) {
            return AbilityTriggerResult.empty();
        }

        Map<String, Object> sharedData = eventData == null ? Map.of() : eventData;
        DamageContext damageContext = resolveDamageContext(sharedData);
        boolean anyActivated = false;

        for (PetComponent component : pets) {
            if (component == null) {
                continue;
            }
            MobEntity pet = component.getPetEntity();
            if (pet == null || pet.isRemoved()) {
                continue;
            }

            ServerPlayerEntity resolvedOwner = resolveOwner(ownerHint, component);
            if (resolvedOwner == null) {
                continue;
            }

            List<CompiledAbility> compiled = resolveComponentAbilities(component, trimmedTrigger);
            if (compiled.isEmpty()) {
                continue;
            }

            TriggerContext context = new TriggerContext(world, pet, resolvedOwner, trimmedTrigger);
            if (!sharedData.isEmpty()) {
                context.getEventData().putAll(sharedData);
            }
            if (damageContext.hasContext()) {
                context.withDamageContext(
                    damageContext.source(),
                    damageContext.damageAmount(),
                    damageContext.lethal(),
                    damageContext.result()
                );
            }

            AbilityTriggerResult result = executeCompiledAbilities(component, compiled, context);
            anyActivated |= result.anyActivated();
        }

        return AbilityTriggerResult.of(anyActivated, damageContext.result());
    }

    public static AbilityExecutionPlan prepareOwnerExecutionPlan(OwnerBatchSnapshot snapshot,
                                                                 AbilityTriggerPayload payload) {
        if (snapshot == null || payload == null) {
            return AbilityExecutionPlan.empty();
        }
        List<OwnerBatchSnapshot.PetSummary> pets = snapshot.pets();
        if (pets.isEmpty()) {
            return AbilityExecutionPlan.empty();
        }
        String triggerId = payload.eventType();
        if (triggerId.isEmpty()) {
            return AbilityExecutionPlan.empty();
        }

        List<AbilityExecutionPlan.PetExecution> executions = new ArrayList<>();

        for (OwnerBatchSnapshot.PetSummary petSummary : pets) {
            UUID petUuid = petSummary.petUuid();
            if (petUuid == null) {
                continue;
            }

            List<Identifier> abilityIds = petSummary.abilities();
            if (abilityIds == null || abilityIds.isEmpty()) {
                continue;
            }

            List<CompiledAbility> compiled = compileAbilitiesForEvent(abilityIds, triggerId);
            if (compiled.isEmpty()) {
                continue;
            }

            Map<String, Long> cooldowns = petSummary.cooldowns();
            executions.add(new AbilityExecutionPlan.PetExecution(petUuid, compiled, cooldowns));
        }

        return AbilityExecutionPlan.fromEntries(executions);
    }

    public static AbilityTriggerResult applyOwnerExecutionPlan(AbilityExecutionPlan plan,
                                                               StateManager stateManager,
                                                               AbilityTriggerPayload payload,
                                                               @Nullable UUID ownerId) {
        if (plan == null || plan.isEmpty() || stateManager == null) {
            return AbilityTriggerResult.empty();
        }

        ServerWorld world = stateManager.world();
        if (world == null) {
            return AbilityTriggerResult.empty();
        }

        List<PetSwarmIndex.SwarmEntry> swarm = ownerId != null
            ? stateManager.getSwarmIndex().snapshotOwner(ownerId)
            : List.of();
        if (swarm.isEmpty()) {
            return AbilityTriggerResult.empty();
        }

        long applicationTick = world.getTime();

        Map<UUID, PetComponent> componentsById = new HashMap<>();
        Map<UUID, MobEntity> entitiesById = new HashMap<>();
        for (PetSwarmIndex.SwarmEntry entry : swarm) {
            MobEntity pet = entry.pet();
            if (pet == null) {
                continue;
            }
            componentsById.put(pet.getUuid(), entry.component());
            entitiesById.put(pet.getUuid(), pet);
        }

        ServerPlayerEntity owner = stateManager.findOnlineOwner(ownerId);

        Map<String, Object> payloadData = payload.hasData() ? payload.eventData() : Map.of();
        DamageContext damageContext = resolveDamageContext(payloadData);
        boolean anyActivated = false;

        for (AbilityExecutionPlan.PetExecution execution : plan.executions()) {
            PetComponent component = componentsById.get(execution.petUuid());
            MobEntity pet = entitiesById.get(execution.petUuid());
            if (component == null || pet == null || pet.isRemoved()) {
                continue;
            }

            Map<String, Long> cooldowns = component.copyCooldownSnapshot();
            if (cooldowns.isEmpty()) {
                cooldowns = execution.cooldowns();
            }

            List<CompiledAbility> liveAbilities = resolveComponentAbilities(component, payload.eventType());
            List<CompiledAbility> merged = mergeCompiledAbilities(execution.abilities(), liveAbilities);
            if (merged.isEmpty()) {
                continue;
            }

            List<CompiledAbility> readyAbilities = filterAbilitiesByCooldown(
                merged,
                cooldowns,
                applicationTick
            );
            if (readyAbilities.isEmpty()) {
                continue;
            }
            TriggerContext context = new TriggerContext(world, pet, owner, payload.eventType());
            if (!payloadData.isEmpty()) {
                context.getEventData().putAll(payloadData);
            }
            if (damageContext.hasContext()) {
                context.withDamageContext(
                    damageContext.source(),
                    damageContext.damageAmount(),
                    damageContext.lethal(),
                    damageContext.result()
                );
            }
            AbilityTriggerResult result = executeCompiledAbilities(component, readyAbilities, context);
            anyActivated |= result.anyActivated();
        }
        return AbilityTriggerResult.of(anyActivated, damageContext.result());
    }

    /**
     * Get a specific ability by ID.
     */
    public static Ability getAbility(Identifier id) {
        return ALL_ABILITIES.get(id);
    }

    private static String resolveEventKey(Ability ability) {
        if (ability == null) {
            return null;
        }
        if (ability.getTrigger() == null) {
            return null;
        }
        Identifier triggerId = ability.getTrigger().getId();
        if (triggerId == null) {
            return null;
        }
        String path = triggerId.getPath();
        if (path == null || path.isEmpty()) {
            return null;
        }
        return path;
    }

    private static final class CompiledAbility {
        private final Ability ability;
        private final Identifier id;
        private final String cooldownKey;
        private final int baseCooldown;

        private CompiledAbility(Ability ability, Identifier id, String cooldownKey, int baseCooldown) {
            this.ability = ability;
            this.id = id;
            this.cooldownKey = cooldownKey;
            this.baseCooldown = baseCooldown;
        }

        static CompiledAbility from(Ability ability) {
            Identifier id = ability.getId();
            if (id == null) {
                String fallbackPath = ability.getClass().getSimpleName().toLowerCase(Locale.ROOT);
                id = Identifier.of(Petsplus.MOD_ID, "synthetic/" + fallbackPath);
            }
            String cooldownKey = id.toString();
            int baseCooldown = 0;
            if (ability.getTrigger() != null) {
                baseCooldown = ability.getTrigger().getInternalCooldownTicks();
            }
            return new CompiledAbility(ability, id, cooldownKey, baseCooldown);
        }

        Ability ability() {
            return ability;
        }

        Identifier id() {
            return id;
        }

        String cooldownKey() {
            return cooldownKey;
        }

        int baseCooldown() {
            return baseCooldown;
        }
    }

    private record AbilityDispatchEntry(CompiledAbility compiled, @Nullable String eventKey) {
        static AbilityDispatchEntry from(Ability ability, @Nullable String eventKey) {
            return new AbilityDispatchEntry(CompiledAbility.from(ability), eventKey);
        }
    }

    private static TriggerContext cloneContextForPet(MobEntity pet, TriggerContext context) {
        TriggerContext petContext = new TriggerContext(
            context.getEntityWorld(),
            pet,
            context.getOwner(),
            context.getEventType()
        );
        if (!context.getEventData().isEmpty()) {
            petContext.getEventData().putAll(context.getEventData());
        }
        if (context.hasDamageContext()) {
            petContext.withDamageContext(
                context.getIncomingDamageSource(),
                context.getIncomingDamageAmount(),
                context.isLethalDamage(),
                context.getDamageResult()
            );
        }
        return petContext;
    }

    private static List<CompiledAbility> resolveComponentAbilities(PetComponent component,
                                                                   @Nullable String eventType) {
        if (component == null) {
            return List.of();
        }
        ProgressionModule progression = component.getProgressionModule();
        if (progression == null) {
            return List.of();
        }
        Set<Identifier> unlocked = progression.getUnlockedAbilities();
        if (unlocked == null || unlocked.isEmpty()) {
            return List.of();
        }
        return compileAbilitiesForEvent(unlocked, eventType);
    }

    private static List<CompiledAbility> compileAbilitiesForEvent(Collection<Identifier> abilityIds,
                                                                  @Nullable String eventType) {
        if (abilityIds == null || abilityIds.isEmpty()) {
            return List.of();
        }

        List<Identifier> ordered = new ArrayList<>(abilityIds.size());
        for (Identifier abilityId : abilityIds) {
            if (abilityId != null) {
                ordered.add(abilityId);
            }
        }
        if (ordered.isEmpty()) {
            return List.of();
        }
        ordered.sort(Comparator.comparing(Identifier::toString));

        String trimmedEvent = eventType == null ? "" : eventType.trim();
        boolean hasEvent = !trimmedEvent.isEmpty();

        List<CompiledAbility> matches = new ArrayList<>();
        List<CompiledAbility> fallback = new ArrayList<>();

        for (Identifier abilityId : ordered) {
            AbilityDispatchEntry entry = DISPATCH_ENTRIES.get(abilityId);
            if (entry == null) {
                Petsplus.LOGGER.warn("Unlocked ability {} is not registered for dispatch", abilityId);
                continue;
            }
            String entryEvent = entry.eventKey();
            if (entryEvent == null || entryEvent.isEmpty()) {
                fallback.add(entry.compiled());
            } else if (!hasEvent || entryEvent.equals(trimmedEvent)) {
                matches.add(entry.compiled());
            }
        }

        if (matches.isEmpty()) {
            if (fallback.isEmpty()) {
                return List.of();
            }
            return List.copyOf(fallback);
        }
        if (!fallback.isEmpty()) {
            matches.addAll(fallback);
        }
        return List.copyOf(matches);
    }

    private static List<CompiledAbility> mergeCompiledAbilities(List<CompiledAbility> planned,
                                                                List<CompiledAbility> live) {
        if ((planned == null || planned.isEmpty()) && (live == null || live.isEmpty())) {
            return List.of();
        }
        if (planned == null || planned.isEmpty()) {
            return live == null ? List.of() : live;
        }
        if (live == null || live.isEmpty()) {
            return planned;
        }

        Map<Identifier, CompiledAbility> merged = new LinkedHashMap<>();
        for (CompiledAbility ability : planned) {
            if (ability != null) {
                merged.putIfAbsent(ability.id(), ability);
            }
        }
        for (CompiledAbility ability : live) {
            if (ability != null) {
                merged.putIfAbsent(ability.id(), ability);
            }
        }
        if (merged.isEmpty()) {
            return List.of();
        }
        return List.copyOf(merged.values());
    }

    private static DamageContext resolveDamageContext(@Nullable Map<String, Object> eventData) {
        if (eventData == null || eventData.isEmpty()) {
            return DamageContext.EMPTY;
        }
        DamageSource source = null;
        Object sourceObject = eventData.get(EVENT_DAMAGE_SOURCE);
        if (sourceObject instanceof DamageSource damageSource) {
            source = damageSource;
        }
        double amount = 0.0D;
        Object amountObject = eventData.get(EVENT_DAMAGE);
        if (amountObject instanceof Number number) {
            amount = number.doubleValue();
        }
        boolean lethal = Boolean.TRUE.equals(eventData.get(EVENT_LETHAL_DAMAGE));
        boolean intercept = Boolean.TRUE.equals(eventData.get(EVENT_INTERCEPT_DAMAGE));
        DamageInterceptionResult existingResult = null;
        Object resultObject = eventData.get(EVENT_DAMAGE_RESULT);
        if (resultObject instanceof DamageInterceptionResult interceptionResult) {
            existingResult = interceptionResult;
        }
        DamageInterceptionResult result = existingResult;
        if (result == null && (lethal || intercept)) {
            result = new DamageInterceptionResult(amount);
        }
        if (source == null && amount <= 0.0D && !lethal && !intercept && result == null) {
            return DamageContext.EMPTY;
        }
        return new DamageContext(source, Math.max(0.0D, amount), lethal, result);
    }

    private record DamageContext(@Nullable DamageSource source,
                                 double damageAmount,
                                 boolean lethal,
                                 @Nullable DamageInterceptionResult result) {
        private static final DamageContext EMPTY = new DamageContext(null, 0.0D, false, null);

        boolean hasContext() {
            return source != null || damageAmount > 0.0D || lethal || result != null;
        }
    }

    private static AbilityTriggerResult executeCompiledAbilities(PetComponent component,
                                                                 List<CompiledAbility> compiledAbilities,
                                                                 TriggerContext context) {
        if (compiledAbilities == null || compiledAbilities.isEmpty()) {
            return AbilityTriggerResult.empty();
        }

        boolean anyActivated = false;

        for (CompiledAbility entry : compiledAbilities) {
            Ability ability = entry.ability();
            String cooldownKey = entry.cooldownKey();
            boolean onCooldown = component.isOnCooldown(cooldownKey);
            int baseCooldown = entry.baseCooldown();

            AbilityActivationEvent.Context eventContext = new AbilityActivationEvent.Context(
                ability,
                context,
                component,
                null,
                onCooldown,
                baseCooldown
            );

            AbilityActivationEvent.firePre(eventContext);

            if (eventContext.isCancelled()) {
                continue;
            }

            if (onCooldown && !eventContext.shouldBypassCooldown()) {
                continue;
            }

            boolean succeeded;
            if (eventContext.shouldRunDefaultExecution()) {
                succeeded = ability.tryActivate(context);
                eventContext.setSucceeded(succeeded);
            } else {
                succeeded = eventContext.didSucceed();
            }

            AbilityActivationEvent.firePost(eventContext);

            boolean applyCooldown = eventContext.shouldApplyCooldown()
                && (eventContext.didSucceed() || eventContext.shouldApplyCooldownOnFailure());

            if (applyCooldown) {
                int cooldown = eventContext.getCooldownTicks();
                if (cooldown > 0) {
                    component.setCooldown(cooldownKey, cooldown);
                } else {
                    component.clearCooldown(cooldownKey);
                }
            }

            if (eventContext.didSucceed()) {
                anyActivated = true;
            }
        }
        return AbilityTriggerResult.of(anyActivated, context.getDamageResult());
    }

    @Nullable
    private static ServerPlayerEntity resolveOwner(@Nullable ServerPlayerEntity ownerHint, PetComponent component) {
        if (ownerHint != null && !ownerHint.isRemoved()) {
            return ownerHint;
        }
        PlayerEntity fallback = component.getOwner();
        if (fallback instanceof ServerPlayerEntity serverOwner && !serverOwner.isRemoved()) {
            return serverOwner;
        }
        return null;
    }

    private static List<CompiledAbility> filterAbilitiesByCooldown(
        List<CompiledAbility> compiled,
        Map<String, Long> cooldowns,
        long snapshotTick
    ) {
        if (compiled == null || compiled.isEmpty()) {
            return List.of();
        }
        if (cooldowns == null || cooldowns.isEmpty()) {
            return compiled;
        }
        List<CompiledAbility> filtered = new ArrayList<>(compiled.size());
        for (CompiledAbility ability : compiled) {
            Long cooldownEnd = cooldowns.get(ability.cooldownKey());
            if (cooldownEnd != null && cooldownEnd > snapshotTick) {
                continue;
            }
            filtered.add(ability);
        }
        if (filtered.isEmpty()) {
            return List.of();
        }
        if (filtered.size() == compiled.size()) {
            return compiled;
        }
        return List.copyOf(filtered);
    }

    public static final class AbilityExecutionPlan {
        private static final AbilityExecutionPlan EMPTY = new AbilityExecutionPlan(List.of());

        private final List<PetExecution> executions;

        private AbilityExecutionPlan(List<PetExecution> executions) {
            this.executions = executions;
        }

        static AbilityExecutionPlan empty() {
            return EMPTY;
        }

        static AbilityExecutionPlan fromEntries(List<PetExecution> executions) {
            if (executions == null || executions.isEmpty()) {
                return EMPTY;
            }
            return new AbilityExecutionPlan(List.copyOf(executions));
        }

        public boolean isEmpty() {
            return executions.isEmpty();
        }

        public List<PetExecution> executions() {
            return executions;
        }

        public static final class PetExecution {
            private final UUID petUuid;
            private final List<CompiledAbility> abilities;
            private final Map<String, Long> cooldowns;

            public PetExecution(UUID petUuid,
                                 List<CompiledAbility> abilities,
                                 Map<String, Long> cooldowns) {
                this.petUuid = petUuid;
                this.abilities = abilities == null || abilities.isEmpty()
                    ? List.of()
                    : List.copyOf(abilities);
                this.cooldowns = cooldowns == null || cooldowns.isEmpty()
                    ? Map.of()
                    : Map.copyOf(cooldowns);
            }

            public UUID petUuid() {
                return petUuid;
            }

            public List<CompiledAbility> abilities() {
                return abilities;
            }

            public Map<String, Long> cooldowns() {
                return cooldowns;
            }
        }
    }

    /**
     * Trigger an ability for testing purposes.
     */
    public static boolean triggerAbilityForTest(String abilityId, net.minecraft.server.network.ServerPlayerEntity player) {
        try {
            Identifier id = Identifier.of("petsplus", abilityId);
            Ability ability = ALL_ABILITIES.get(id);

            if (ability != null) {
                Petsplus.LOGGER.info("Test triggered ability: {}", abilityId);
                return true;
            }

            return false;
        } catch (Exception e) {
            Petsplus.LOGGER.error("Error triggering ability for test", e);
            return false;
        }
    }
}


