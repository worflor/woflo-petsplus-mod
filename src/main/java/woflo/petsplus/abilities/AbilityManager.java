package woflo.petsplus.abilities;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Ability;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.event.AbilityActivationEvent;
import woflo.petsplus.api.registry.AbilityType;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PetSwarmIndex;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.state.processing.OwnerBatchSnapshot;

import java.util.*;
import org.jetbrains.annotations.Nullable;

/**
 * Manages all abilities and their activation for pets.
 */
public class AbilityManager {
    private static final Map<Identifier, List<Ability>> ROLE_ABILITIES = new HashMap<>();
    private static final Map<Identifier, Ability> ALL_ABILITIES = new HashMap<>();
    private static final Map<Identifier, RoleAbilityCache> ROLE_EVENT_CACHES = new HashMap<>();
    private static final Map<Identifier, List<RoleAbilityCache.CompiledAbility>> ROLE_COMPILED_DEFAULTS = new HashMap<>();

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
        ROLE_ABILITIES.clear();
        ALL_ABILITIES.clear();
        ROLE_EVENT_CACHES.clear();
        ROLE_COMPILED_DEFAULTS.clear();

        Map<Identifier, Ability> instantiated = new HashMap<>();
        for (AbilityType type : PetsPlusRegistries.abilityTypeRegistry()) {
            Ability ability = type.createAbility();
            if (ability == null) {
                Petsplus.LOGGER.warn("Registry ability {} returned null instance", type.id());
                continue;
            }
            instantiated.put(type.id(), ability);
            ALL_ABILITIES.put(type.id(), ability);
        }

        for (PetRoleType roleType : PetsPlusRegistries.petRoleTypeRegistry()) {
            List<Ability> loadout = new ArrayList<>();
            Map<String, List<Ability>> eventBuckets = new HashMap<>();
            List<Ability> fallback = new ArrayList<>();

            for (Identifier abilityId : roleType.defaultAbilities()) {
                Ability ability = instantiated.get(abilityId);
                if (ability == null) {
                    Petsplus.LOGGER.error("Role {} references unknown ability {}", roleType.id(), abilityId);
                    continue;
                }
                loadout.add(ability);

                String eventKey = resolveEventKey(ability);
                if (eventKey != null) {
                    eventBuckets.computeIfAbsent(eventKey, unused -> new ArrayList<>()).add(ability);
                } else {
                    fallback.add(ability);
                }
            }

            RoleAbilityCache cache = RoleAbilityCache.build(loadout, eventBuckets, fallback);
            ROLE_ABILITIES.put(roleType.id(), cache.abilityView());
            ROLE_EVENT_CACHES.put(roleType.id(), cache);
            ROLE_COMPILED_DEFAULTS.put(roleType.id(), cache.defaultCompiled());
        }

        if (ALL_ABILITIES.isEmpty()) {
            Petsplus.LOGGER.debug("Ability registry reload completed with no abilities available.");
        } else {
            Petsplus.LOGGER.info("Loaded {} abilities across {} roles", ALL_ABILITIES.size(), ROLE_ABILITIES.size());
        }
    }

    /**
     * Trigger abilities for a pet based on the given context.
     */
    public static void triggerAbilities(MobEntity pet, TriggerContext context) {
        if (pet == null) {
            return;
        }

        PetComponent component = PetComponent.get(pet);
        if (component == null) {
            return;
        }

        Identifier roleId = component.getRoleId();
        RoleAbilityCache cache = ROLE_EVENT_CACHES.get(roleId);
        List<RoleAbilityCache.CompiledAbility> compiledAbilities = cache != null
            ? cache.compiledAbilitiesForEvent(context.getEventType())
            : ROLE_COMPILED_DEFAULTS.get(roleId);

        if (compiledAbilities == null || compiledAbilities.isEmpty()) {
            if (PetsPlusRegistries.petRoleTypeRegistry().get(roleId) == null) {
                Petsplus.LOGGER.warn("Pet {} has role {} without a registered definition; skipping ability triggers.", pet.getUuid(), roleId);
            }
            return;
        }

        TriggerContext petContext = cloneContextForPet(pet, context);
        executeCompiledAbilities(component, component.getRoleType(false), compiledAbilities, petContext);
    }

    /**
     * Triggers a compiled ability batch for an owner-scoped event by grouping pets by
     * role and reusing compiled ability metadata for the entire swarm.
     */
    public static void triggerAbilitiesForOwnerEvent(ServerWorld world,
                                                     @Nullable ServerPlayerEntity ownerHint,
                                                     List<PetComponent> pets,
                                                     String triggerId,
                                                     Map<String, Object> eventData) {
        if (world == null || triggerId == null) {
            return;
        }
        String trimmedTrigger = triggerId.trim();
        if (trimmedTrigger.isEmpty() || pets == null || pets.isEmpty()) {
            return;
        }

        Map<Identifier, RoleAbilityGroup> groups = new HashMap<>();
        Set<Identifier> skippedRoles = new HashSet<>();

        for (PetComponent component : pets) {
            if (component == null) {
                continue;
            }
            MobEntity pet = component.getPetEntity();
            if (pet == null || pet.isRemoved()) {
                continue;
            }

            Identifier roleId = component.getRoleId();
            if (roleId == null) {
                continue;
            }
            if (skippedRoles.contains(roleId)) {
                continue;
            }

            RoleAbilityGroup group = groups.get(roleId);
            if (group == null) {
                RoleAbilityCache cache = ROLE_EVENT_CACHES.get(roleId);
                List<RoleAbilityCache.CompiledAbility> compiled = cache != null
                    ? cache.compiledAbilitiesForEvent(trimmedTrigger)
                    : ROLE_COMPILED_DEFAULTS.get(roleId);
                if (compiled == null || compiled.isEmpty()) {
                    skippedRoles.add(roleId);
                    continue;
                }
                PetRoleType roleType = component.getRoleType(false);
                if (roleType == null) {
                    skippedRoles.add(roleId);
                    continue;
                }
                group = new RoleAbilityGroup(roleType, compiled);
                groups.put(roleId, group);
            }

            ServerPlayerEntity resolvedOwner = resolveOwner(ownerHint, component);
            if (resolvedOwner == null) {
                continue;
            }

            group.addMember(pet, component, resolvedOwner);
        }

        if (groups.isEmpty()) {
            return;
        }

        Map<String, Object> sharedData = eventData == null ? Map.of() : eventData;
        for (RoleAbilityGroup group : groups.values()) {
            for (RoleAbilityGroup.Member member : group.members()) {
                TriggerContext context = new TriggerContext(world, member.pet(), member.owner(), trimmedTrigger);
                if (!sharedData.isEmpty()) {
                    context.getEventData().putAll(sharedData);
                }
                executeCompiledAbilities(member.component(), group.roleType(), group.compiledAbilities(), context);
            }
        }
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
            Identifier roleId = petSummary.roleId();
            if (petUuid == null || roleId == null) {
                continue;
            }

            RoleAbilityCache cache = ROLE_EVENT_CACHES.get(roleId);
            if (cache == null) {
                continue;
            }
            List<RoleAbilityCache.CompiledAbility> compiled = cache.compiledAbilitiesForEvent(triggerId);
            if (compiled == null || compiled.isEmpty()) {
                continue;
            }

            Map<String, Long> cooldowns = petSummary.cooldowns();
            executions.add(new AbilityExecutionPlan.PetExecution(petUuid, roleId, compiled, cooldowns));
        }

        return AbilityExecutionPlan.fromEntries(executions);
    }

    public static void applyOwnerExecutionPlan(AbilityExecutionPlan plan,
                                               StateManager stateManager,
                                               AbilityTriggerPayload payload,
                                               @Nullable UUID ownerId) {
        if (plan == null || plan.isEmpty() || stateManager == null) {
            return;
        }

        ServerWorld world = stateManager.world();
        if (world == null) {
            return;
        }

        List<PetSwarmIndex.SwarmEntry> swarm = ownerId != null
            ? stateManager.getSwarmIndex().snapshotOwner(ownerId)
            : List.of();
        if (swarm.isEmpty()) {
            return;
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

            List<RoleAbilityCache.CompiledAbility> readyAbilities = filterAbilitiesByCooldown(
                execution.abilities(),
                cooldowns,
                applicationTick
            );
            if (readyAbilities.isEmpty()) {
                continue;
            }
            TriggerContext context = new TriggerContext(world, pet, owner, payload.eventType());
            if (payload.hasData()) {
                context.getEventData().putAll(payload.eventData());
            }
            executeCompiledAbilities(component, component.getRoleType(false), readyAbilities, context);
        }
    }

    /**
     * Get all abilities for a specific role.
     */
    public static List<Ability> getAbilitiesForRole(Identifier roleId) {
        return ROLE_ABILITIES.getOrDefault(roleId, Collections.emptyList());
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

    private static final class RoleAbilityCache {
        private final List<Ability> abilityView;
        private final List<CompiledAbility> defaultCompiled;
        private final Map<String, List<CompiledAbility>> compiledByEvent;

        private RoleAbilityCache(List<Ability> abilityView,
                                 List<CompiledAbility> defaultCompiled,
                                 Map<String, List<CompiledAbility>> compiledByEvent) {
            this.abilityView = abilityView;
            this.defaultCompiled = defaultCompiled;
            this.compiledByEvent = compiledByEvent;
        }

        static RoleAbilityCache build(List<Ability> loadout,
                                      Map<String, List<Ability>> eventBuckets,
                                      List<Ability> fallback) {
            List<Ability> abilityView = loadout.isEmpty() ? Collections.emptyList() : List.copyOf(loadout);
            if (eventBuckets.isEmpty() && fallback.isEmpty()) {
                return new RoleAbilityCache(
                    abilityView,
                    compileAbilities(abilityView),
                    Collections.emptyMap()
                );
            }

            IdentityHashMap<Ability, CompiledAbility> compiledCache = new IdentityHashMap<>();
            List<CompiledAbility> baseline = compileAbilities(abilityView, compiledCache);
            List<CompiledAbility> fallbackCompiled = compileAbilities(fallback, compiledCache);

            Map<String, List<CompiledAbility>> compiled = new HashMap<>();
            for (Map.Entry<String, List<Ability>> entry : eventBuckets.entrySet()) {
                List<Ability> abilities = entry.getValue();
                if (abilities == null || abilities.isEmpty()) {
                    continue;
                }
                List<CompiledAbility> eventCompiled = compileAbilities(abilities, compiledCache);
                List<CompiledAbility> merged;
                if (fallbackCompiled.isEmpty()) {
                    merged = eventCompiled;
                } else {
                    merged = new ArrayList<>(eventCompiled.size() + fallbackCompiled.size());
                    merged.addAll(eventCompiled);
                    merged.addAll(fallbackCompiled);
                    merged = Collections.unmodifiableList(merged);
                }
                compiled.put(entry.getKey(), merged);
            }

            return new RoleAbilityCache(
                abilityView,
                baseline,
                compiled.isEmpty() ? Collections.emptyMap() : Map.copyOf(compiled)
            );
        }

        List<Ability> abilityView() {
            return abilityView;
        }

        List<CompiledAbility> defaultCompiled() {
            return defaultCompiled;
        }

        List<CompiledAbility> compiledAbilitiesForEvent(String eventType) {
            if (eventType != null) {
                List<CompiledAbility> abilities = compiledByEvent.get(eventType);
                if (abilities != null) {
                    return abilities;
                }
            }
            return defaultCompiled;
        }

        private static List<CompiledAbility> compileAbilities(List<Ability> abilities) {
            return compileAbilities(abilities, new IdentityHashMap<>());
        }

        private static List<CompiledAbility> compileAbilities(List<Ability> abilities,
                                                              IdentityHashMap<Ability, CompiledAbility> cache) {
            if (abilities == null || abilities.isEmpty()) {
                return Collections.emptyList();
            }
            List<CompiledAbility> compiled = new ArrayList<>(abilities.size());
            for (Ability ability : abilities) {
                if (ability == null) {
                    continue;
                }
                CompiledAbility entry = cache.computeIfAbsent(ability, CompiledAbility::from);
                compiled.add(entry);
            }
            if (compiled.isEmpty()) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(compiled);
        }

        static final class CompiledAbility {
            private final Ability ability;
            private final String cooldownKey;
            private final int baseCooldown;

            private CompiledAbility(Ability ability, String cooldownKey, int baseCooldown) {
                this.ability = ability;
                this.cooldownKey = cooldownKey;
                this.baseCooldown = baseCooldown;
            }

            static CompiledAbility from(Ability ability) {
                Identifier id = ability.getId();
                String cooldownKey = id != null ? id.toString() : ability.getClass().getName();
                int baseCooldown = 0;
                if (ability.getTrigger() != null) {
                    baseCooldown = ability.getTrigger().getInternalCooldownTicks();
                }
                return new CompiledAbility(ability, cooldownKey, baseCooldown);
            }

            Ability ability() {
                return ability;
            }

            String cooldownKey() {
                return cooldownKey;
            }

            int baseCooldown() {
                return baseCooldown;
            }
        }
    }

    private static TriggerContext cloneContextForPet(MobEntity pet, TriggerContext context) {
        TriggerContext petContext = new TriggerContext(
            context.getWorld(),
            pet,
            context.getOwner(),
            context.getEventType()
        );
        if (!context.getEventData().isEmpty()) {
            petContext.getEventData().putAll(context.getEventData());
        }
        return petContext;
    }

    private static void executeCompiledAbilities(PetComponent component,
                                                 @Nullable PetRoleType roleType,
                                                 List<RoleAbilityCache.CompiledAbility> compiledAbilities,
                                                 TriggerContext context) {
        if (compiledAbilities == null || compiledAbilities.isEmpty()) {
            return;
        }

        for (RoleAbilityCache.CompiledAbility entry : compiledAbilities) {
            Ability ability = entry.ability();
            String cooldownKey = entry.cooldownKey();
            boolean onCooldown = component.isOnCooldown(cooldownKey);
            int baseCooldown = entry.baseCooldown();

            AbilityActivationEvent.Context eventContext = new AbilityActivationEvent.Context(
                ability,
                context,
                component,
                roleType,
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
        }
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

    private static List<RoleAbilityCache.CompiledAbility> filterAbilitiesByCooldown(
        List<RoleAbilityCache.CompiledAbility> compiled,
        Map<String, Long> cooldowns,
        long snapshotTick
    ) {
        if (compiled == null || compiled.isEmpty()) {
            return List.of();
        }
        if (cooldowns == null || cooldowns.isEmpty()) {
            return compiled;
        }
        List<RoleAbilityCache.CompiledAbility> filtered = new ArrayList<>(compiled.size());
        for (RoleAbilityCache.CompiledAbility ability : compiled) {
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
            private final Identifier roleId;
            private final List<RoleAbilityCache.CompiledAbility> abilities;
            private final Map<String, Long> cooldowns;

            public PetExecution(UUID petUuid,
                                 Identifier roleId,
                                 List<RoleAbilityCache.CompiledAbility> abilities,
                                 Map<String, Long> cooldowns) {
                this.petUuid = petUuid;
                this.roleId = roleId;
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

            public Identifier roleId() {
                return roleId;
            }

            public List<RoleAbilityCache.CompiledAbility> abilities() {
                return abilities;
            }

            public Map<String, Long> cooldowns() {
                return cooldowns;
            }
        }
    }

    private static final class RoleAbilityGroup {
        private final PetRoleType roleType;
        private final List<RoleAbilityCache.CompiledAbility> compiledAbilities;
        private final List<Member> members = new ArrayList<>();

        RoleAbilityGroup(PetRoleType roleType, List<RoleAbilityCache.CompiledAbility> compiledAbilities) {
            this.roleType = roleType;
            this.compiledAbilities = compiledAbilities;
        }

        void addMember(MobEntity pet, PetComponent component, ServerPlayerEntity owner) {
            members.add(new Member(pet, component, owner));
        }

        PetRoleType roleType() {
            return roleType;
        }

        List<RoleAbilityCache.CompiledAbility> compiledAbilities() {
            return compiledAbilities;
        }

        List<Member> members() {
            return members;
        }

        private record Member(MobEntity pet, PetComponent component, ServerPlayerEntity owner) {
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
