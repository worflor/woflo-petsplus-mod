package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.api.event.PetLevelUpEvent;
import woflo.petsplus.api.event.XpAwardEvent;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RoleIdentifierUtil;
import woflo.petsplus.advancement.AdvancementCriteriaRegistry;
import woflo.petsplus.advancement.BestFriendTracker;
import woflo.petsplus.Petsplus;
import woflo.petsplus.history.HistoryManager;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.coordination.PetSwarmIndex;
import woflo.petsplus.state.StateManager;
import woflo.petsplus.stats.PetAttributeManager;
import woflo.petsplus.state.processing.OwnerEventFrame;
import woflo.petsplus.state.processing.OwnerEventType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Handles pet XP gain when owners gain XP.
 */
public class XpEventHandler {
    private static final Map<PlayerEntity, Long> LAST_COMBAT_TIME = new WeakHashMap<>();
    private static final Map<MobEntity, Long> LAST_PET_COMBAT = new WeakHashMap<>();
    
    public static void initialize() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LAST_COMBAT_TIME.clear();
            LAST_PET_COMBAT.clear();
        });
    }

    public static void onExperienceGained(ServerPlayerEntity player, int xpGained) {
        if (player == null || xpGained <= 0) {
            return;
        }
        handlePlayerXpGain(player, xpGained);
    }

    private static void handlePlayerXpGain(ServerPlayerEntity player, int xpGained) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        StateManager stateManager = StateManager.forWorld(world);
        EnumSet<OwnerEventType> eventTypes = EnumSet.of(OwnerEventType.XP_GAIN);
        EnumMap<OwnerEventType, Object> payload = new EnumMap<>(OwnerEventType.class);
        payload.put(OwnerEventType.XP_GAIN, new XpGainPayload(xpGained));
        stateManager.dispatchOwnerEvents(player, eventTypes, payload);
    }

    public static void handleOwnerXpGainEvent(OwnerEventFrame frame) {
        if (frame == null || frame.eventType() != OwnerEventType.XP_GAIN) {
            return;
        }
        ServerPlayerEntity owner = frame.owner();
        if (owner == null || owner.isRemoved()) {
            return;
        }
        XpGainPayload payload = frame.payload(XpGainPayload.class);
        if (payload == null || payload.xpAmount() <= 0) {
            return;
        }
        distributeXpToSwarm(frame, owner, payload.xpAmount());
    }

    private static void distributeXpToSwarm(OwnerEventFrame frame, ServerPlayerEntity owner, int xpGained) {
        List<PetSwarmIndex.SwarmEntry> swarmEntries = frame.swarmSnapshot();
        if (swarmEntries.isEmpty()) {
            return;
        }

        Vec3d center = owner.getEntityPos();
        double radius = 32.0;
        double radiusSq = radius * radius;

        PetsPlusConfig config = PetsPlusConfig.getInstance();
        double baseXpModifier = config.getSectionDouble("pet_leveling", "xp_modifier", 1.0);

        // Single pass: filter eligible pets and cache them
        List<EligiblePetData> eligiblePets = new ArrayList<>();
        for (PetSwarmIndex.SwarmEntry entry : swarmEntries) {
            MobEntity pet = entry.pet();
            PetComponent petComp = entry.component();
            if (pet == null || petComp == null || !pet.isAlive()) {
                continue;
            }

            double dx = entry.x() - center.x;
            double dy = entry.y() - center.y;
            double dz = entry.z() - center.z;
            if ((dx * dx) + (dy * dy) + (dz * dz) > radiusSq) {
                continue;
            }

            eligiblePets.add(new EligiblePetData(pet, petComp));
        }

        if (eligiblePets.isEmpty()) {
            return;
        }

        int eligiblePetCount = eligiblePets.size();

        double positiveBaseModifier = Math.max(0.0, baseXpModifier);
        double baseShare = xpGained / (double) eligiblePetCount;

        double[] weightValues = new double[eligiblePetCount];
        double totalDesiredShare = 0.0;
        for (int i = 0; i < eligiblePetCount; i++) {
            EligiblePetData data = eligiblePets.get(i);
            PetComponent petComp = data.component();
            MobEntity pet = data.pet();

            float levelScaleModifier = getLevelScaleModifier(petComp.getLevel());

            // Comprehensive learning modifier from Nature + Imprint + Role
            float learningModifier = calculateLearningModifier(petComp);

            float participationModifier = getParticipationModifier(owner, pet);

            double weight = positiveBaseModifier * levelScaleModifier * learningModifier * participationModifier;
            double desiredShare = baseShare * weight;
            if (desiredShare > 0.0) {
                totalDesiredShare += desiredShare;
                weightValues[i] = desiredShare;
            } else {
                weightValues[i] = 0.0;
            }
        }

        double xpBudget = Math.min(xpGained, totalDesiredShare);
        if (xpBudget < 0.0) {
            xpBudget = 0.0;
        }

        List<Integer> distributedXp = allocateXpShares(xpBudget, weightValues);

        for (int i = 0; i < eligiblePetCount; i++) {
            int petXpGain = distributedXp.get(i);
            if (petXpGain <= 0) {
                continue;
            }

            EligiblePetData data = eligiblePets.get(i);
            MobEntity pet = data.pet();
            PetComponent petComp = data.component();

            int previousLevel = petComp.getLevel();

            XpAwardEvent.Context xpContext = new XpAwardEvent.Context(
                owner,
                pet,
                petComp,
                xpGained,
                petXpGain,
                XpAwardEvent.OWNER_XP_SHARE
            );
            XpAwardEvent.fire(xpContext);

            if (xpContext.isCancelled() || xpContext.getAmount() <= 0) {
                continue;
            }

            int awardedXp = xpContext.getAmount();
            boolean leveledUp = petComp.addExperience(awardedXp);

            if (leveledUp) {
                int newLevel = petComp.getLevel();
                
                // Handle multiple level-ups (e.g., massive XP gain)
                for (int level = previousLevel + 1; level <= newLevel; level++) {
                    applyLevelRewards(pet, petComp, owner, level);
                }
                
                // Recalculate attributes ONCE after all rewards applied
                PetAttributeManager.applyAttributeModifiers(pet, petComp);
                
                // Trigger pet level criterion for final level reached
                String roleIdStr = petComp.getRoleId() != null ? petComp.getRoleId().toString() : null;
                AdvancementCriteriaRegistry.PET_LEVEL.trigger(owner, newLevel, roleIdStr);
                PetLevelUpEvent.Context levelContext = new PetLevelUpEvent.Context(
                    owner,
                    pet,
                    petComp,
                    previousLevel,
                    petComp.getLevel(),
                    awardedXp
                );
                PetLevelUpEvent.fire(levelContext);

                if (!levelContext.isDefaultCelebrationSuppressed()) {
                    handlePetLevelUp(owner, pet, petComp);
                }

                if (previousLevel < 30 && petComp.getLevel() >= 30) {
                    ServerWorld ownerWorld = (ServerWorld) owner.getEntityWorld();
                    BestFriendTracker tracker = BestFriendTracker.get(ownerWorld);
                    if (tracker.registerBestFriend(ownerWorld, owner.getUuid(), pet.getUuid())) {
                        HistoryManager.recordBestFriendForeverer(pet, owner);
                    }
                }
            }
        }
    }

    private record XpGainPayload(int xpAmount) {}

    private record EligiblePetData(MobEntity pet, PetComponent component) {}

    static List<Integer> allocateXpShares(double xpBudget, double[] weights) {
        int size = weights == null ? 0 : weights.length;
        List<Integer> allocations = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            allocations.add(0);
        }

        double normalizedBudget = Math.max(0.0, xpBudget);
        if (size == 0 || normalizedBudget <= 0.0) {
            return allocations;
        }

        double totalWeight = 0.0;
        double[] normalizedWeights = new double[size];
        for (int i = 0; i < size; i++) {
            double weight = weights[i];
            double clamped = weight > 0.0 ? weight : 0.0;
            normalizedWeights[i] = clamped;
            totalWeight += clamped;
        }

        if (totalWeight <= 0.0) {
            return allocations;
        }

        int xpWholeBudget = (int) Math.floor(normalizedBudget);
        List<ShareRemainder> remainders = new ArrayList<>(size);
        int assigned = 0;
        for (int i = 0; i < size; i++) {
            double weight = normalizedWeights[i];
            if (weight <= 0.0) {
                remainders.add(new ShareRemainder(i, 0.0));
                continue;
            }

            double rawShare = normalizedBudget * (weight / totalWeight);
            int wholeShare = (int) Math.floor(rawShare);
            allocations.set(i, wholeShare);
            assigned += wholeShare;
            remainders.add(new ShareRemainder(i, rawShare - wholeShare));
        }

        int xpRemaining = xpWholeBudget - assigned;
        if (xpRemaining < 0) {
            int surplus = -xpRemaining;
            remainders.sort((a, b) -> {
                int cmp = Double.compare(a.remainder(), b.remainder());
                if (cmp != 0) {
                    return cmp;
                }
                return Integer.compare(b.index(), a.index());
            });
            for (ShareRemainder remainder : remainders) {
                if (surplus <= 0) {
                    break;
                }
                int current = allocations.get(remainder.index());
                if (current <= 0) {
                    continue;
                }
                allocations.set(remainder.index(), current - 1);
                surplus--;
            }
            xpRemaining = 0;
        }

        if (xpRemaining > 0) {
            remainders.sort((a, b) -> {
                int cmp = Double.compare(b.remainder(), a.remainder());
                if (cmp != 0) {
                    return cmp;
                }
                return Integer.compare(a.index(), b.index());
            });
            for (ShareRemainder remainder : remainders) {
                if (xpRemaining <= 0) {
                    break;
                }
                if (normalizedWeights[remainder.index()] <= 0.0) {
                    continue;
                }
                allocations.set(remainder.index(), allocations.get(remainder.index()) + 1);
                xpRemaining--;
            }
            if (xpRemaining > 0) {
                for (int i = 0; i < size && xpRemaining > 0; i++) {
                    if (normalizedWeights[i] <= 0.0) {
                        continue;
                    }
                    allocations.set(i, allocations.get(i) + 1);
                    xpRemaining--;
                }
            }
        }

        return allocations;
    }

    private record ShareRemainder(int index, double remainder) {}
    
    /**
     * Get level-scaled XP modifier for more engaging progression.
     * Early game gets bonus XP for fast bonding, late game is more challenging.
     */
    private static float getLevelScaleModifier(int petLevel) {
        if (petLevel <= 5) {
            return 1.6f;    // 160% XP for levels 1-5 (fast early bonding)
        } else if (petLevel <= 10) {
            return 1.3f;    // 130% XP for levels 6-10 (still generous)
        } else if (petLevel <= 15) {
            return 1.1f;    // 110% XP for levels 11-15 (slight bonus)
        } else if (petLevel <= 20) {
            return 1.0f;    // 100% XP for levels 16-20 (1:1 ratio)
        } else if (petLevel <= 25) {
            return 0.8f;    // 80% XP for levels 21-25 (more challenging)
        } else {
            return 0.7f;    // 70% XP for levels 26-30 (prestigious end-game)
        }
    }
    
    /**
     * Get participation modifier based on recent combat activity.
     * Amplifies XP gain when pets or players are actively fighting.
     */
    private static float getParticipationModifier(ServerPlayerEntity player, MobEntity pet) {
        long currentTime = player.getEntityWorld().getTime();
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        
        // Check for recent combat activity (within last 30 seconds = 600 ticks)
        boolean playerRecentCombat = LAST_COMBAT_TIME.getOrDefault(player, 0L) > (currentTime - 600);
        boolean petRecentCombat = LAST_PET_COMBAT.getOrDefault(pet, 0L) > (currentTime - 600);
        
        float modifier = 1.0f;
        
        // Combat participation bonus - amplifies without punishing passive gameplay
        if (playerRecentCombat || petRecentCombat) {
            float participationBonus = (float) config.getSectionDouble("pet_leveling", "participation_bonus", 0.3);
            modifier += participationBonus; // +30% default for combat participation
        }
        
        return modifier;
    }
    
    /**
     * Call this when a player deals damage to track combat activity.
     */
    public static void trackPlayerCombat(ServerPlayerEntity player) {
        LAST_COMBAT_TIME.put(player, player.getEntityWorld().getTime());
    }
    
    /**
     * Call this when a pet deals damage to track combat activity.
     */
    public static void trackPetCombat(MobEntity pet) {
        LAST_PET_COMBAT.put(pet, pet.getEntityWorld().getTime());
    }
    
    private static void handlePetLevelUp(ServerPlayerEntity owner, MobEntity pet, PetComponent petComp) {
        int newLevel = petComp.getLevel();
        PetRoleType roleType = petComp.getRoleType();

        // No need to manually trigger advancements anymore - the criterion system handles it!
        // The PET_LEVEL criterion is already fired above with proper role/level conditions

        // Trigger role-specific milestones for messages/sounds only
        for (PetRoleType.MilestoneAdvancement advancement : roleType.milestoneAdvancements()) {
            if (advancement.level() == newLevel) {
                sendMilestoneMessage(owner, pet, advancement);
                playMilestoneSound(owner, pet, advancement.sound());
            }
        }
        
        // Play level up sound
        owner.getEntityWorld().playSound(
            null, 
            pet.getX(), pet.getY(), pet.getZ(), 
            SoundEvents.ENTITY_VILLAGER_YES, 
            SoundCategory.NEUTRAL, 
            0.5f, 
            1.2f // Higher pitch to distinguish from player level up
        );
        
        // Send level up message to owner
        String petName = pet.hasCustomName() ? pet.getCustomName().getString() : pet.getType().getName().getString();
        Identifier roleId = petComp.getRoleId();
        String roleDisplayName = roleId != null
            ? RoleIdentifierUtil.roleLabel(roleId, roleType).getString()
            : "Unknown";
        if (roleDisplayName == null || roleDisplayName.isBlank()) {
            roleDisplayName = "Unknown";
        }
        
        if (petComp.isFeatureLevel()) {
            // Special message for feature levels that unlock new abilities
            owner.sendMessage(
                Text.of("§6§l" + petName + " §ereached level §6" + newLevel + "§e! §b" + roleDisplayName + " §eunlocked new abilities!"),
                true // Action bar
            );
        } else {
            // Regular level up message
            owner.sendMessage(
                Text.of("§e" + petName + " §7reached level §e" + newLevel + "§7! (" + roleDisplayName + ")"),
                true // Action bar
            );
        }
        
        // Additional effects for feature levels
        if (petComp.isFeatureLevel()) {
            // Play extra celebratory sound
            owner.getEntityWorld().playSound(
                null,
                pet.getX(), pet.getY(), pet.getZ(),
                SoundEvents.ENTITY_VILLAGER_CELEBRATE,
                SoundCategory.NEUTRAL,
                0.8f,
                1.0f
            );
            
            // Send chat message for major milestones
            owner.sendMessage(
                Text.of("§6[Milestone] §e" + petName + " §6has unlocked new " + roleDisplayName + " abilities at level " + newLevel + "!"),
                false // Chat message
            );

            // Suggest inspecting XP info
            if (owner instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                woflo.petsplus.ui.ChatLinks.sendSuggest(sp,
                    new woflo.petsplus.ui.ChatLinks.Suggest("[View Pet XP]", "/petsplus xp info", "See levels and XP progress", "yellow", true));
            }
        }
    }

    private static void sendMilestoneMessage(ServerPlayerEntity owner, MobEntity pet, PetRoleType.MilestoneAdvancement advancement) {
        PetRoleType.Message message = advancement.message();
        if (message == null || !message.isPresent()) {
            return;
        }
        Text text = buildMilestoneText(message, pet, advancement.level());
        if (text != null) {
            owner.sendMessage(text, false);
        }
    }

    private static Text buildMilestoneText(PetRoleType.Message message, MobEntity pet, int level) {
        return RoleIdentifierUtil.resolveMessageText(message, null, pet.getDisplayName(), level);
    }

    private static void playMilestoneSound(ServerPlayerEntity owner, MobEntity pet, PetRoleType.SoundCue cue) {
        if (cue == null || !cue.isPresent()) {
            return;
        }
        RegistryEntry<SoundEvent> entry = Registries.SOUND_EVENT.getEntry(cue.soundId()).orElse(null);
        if (entry == null) {
            Petsplus.LOGGER.warn("Unknown sound '{}' configured for milestone on pet {}", cue.soundId(), pet.getUuid());
            return;
        }
        owner.getEntityWorld().playSound(null, pet.getX(), pet.getY(), pet.getZ(), entry.value(), SoundCategory.NEUTRAL, cue.volume(), cue.pitch());
    }
    
    /**
     * Calculate learning modifier from Role and Imprint.
     * 
     * <h2>Learning Modifier Pipeline:</h2>
     * <ul>
     *   <li><b>Nature:</b> No XP modifier - natures are pure personality/stat quirks</li>
     *   <li><b>Imprint:</b> Focus stat subtly affects learning (±3% typical range)</li>
     *   <li><b>Role:</b> Intentional choice determines learning aptitude (Scholar +15%, Cursed -10%)</li>
     * </ul>
     * 
     * <p>Design: Learning differences come from player choices (Role) and RNG stats (Imprint),
     * not from uncontrollable circumstances (Nature). Typical range: 0.85x - 1.20x
     * 
     * @return Final learning multiplier
     */
    private static float calculateLearningModifier(PetComponent petComp) {
        float multiplier = 1.0f;
        
        // Nature bonus removed - natures are personality quirks, not skill tiers
        // (Kept method call for potential future config hooks)
        float natureBonus = getNatureLearningBonus(petComp);
        multiplier *= natureBonus;
        
        // Imprint-based learning bonus (subtle focus influence)
        woflo.petsplus.stats.PetImprint imprint = petComp.getImprint();
        if (imprint != null) {
            // Focus multiplier 0.88-1.12 → learning bonus 0.97-1.03 (scaled to ±3%)
            float focusMult = imprint.getFocusMultiplier();
            float imprintBonus = 1.0f + ((focusMult - 1.0f) * 0.25f);
            multiplier *= imprintBonus;
        }
        
        // Role-based learning bonus
        float roleBonus = getRoleLearningBonus(petComp);
        multiplier *= roleBonus;
        
        return multiplier;
    }
    
    /**
     * Get nature-specific learning bonuses.
     * Natures are pure personality quirks - learning differences come from Role choice and Imprint variance.
     * This prevents optimization pressure around uncontrollable breeding/taming circumstances.
     */
    private static float getNatureLearningBonus(PetComponent petComp) {
        // All natures learn equally - personality expressed through stats, emotions, and behavior instead
        return 1.0f;
    }
    
    /**
     * Get role-specific learning bonuses.
     * Roles focused on growth and support learn faster.
     */
    private static float getRoleLearningBonus(PetComponent petComp) {
        woflo.petsplus.api.registry.PetRoleType roleType = petComp.getRoleType(false);
        if (roleType == null) {
            return 1.0f;
        }
        
        net.minecraft.util.Identifier roleId = roleType.id();
        String roleName = roleId.getPath();
        
        return switch (roleName) {
            // Scholar focuses on learning itself
            case "scholar" -> 1.15f;      // +15% XP (dedicated learner)
            // Support roles observe and adapt quickly  
            case "support" -> 1.10f;      // +10% XP (observant healer)
            case "enchantment_bound" -> 1.10f; // +10% XP (studious enchanter)
            // Disciplined protectors
            case "guardian" -> 1.05f;     // +5% XP (disciplined training)
            // Combat-focused roles learn normally
            case "striker" -> 1.00f;      // Neutral (action over study)
            case "skyrider" -> 1.00f;     // Neutral (instinctive movement)
            case "scout" -> 1.00f;        // Neutral (practical explorer)
            // Chaotic/cursed roles struggle
            case "eclipsed" -> 0.95f;     // -5% XP (void-touched chaos)
            case "cursed_one" -> 0.90f;   // -10% XP (cursed existence)
            default -> 1.0f;              // Neutral for custom roles
        };
    }
    
    /**
     * Apply level rewards from the role definition.
     * Note: Attribute recalculation is handled by the caller to batch multiple level-ups.
     */
    private static void applyLevelRewards(MobEntity pet, PetComponent petComp, ServerPlayerEntity owner, int level) {
        PetRoleType roleType = petComp.getRoleType(false);
        if (roleType == null) {
            return;
        }
        
        PetRoleType.Definition definition = roleType.definition();
        if (definition == null) {
            return;
        }
        
        java.util.List<woflo.petsplus.api.LevelReward> rewards = definition.getRewardsForLevel(level);
        if (rewards.isEmpty()) {
            return;
        }
        
        Petsplus.LOGGER.debug("Applying {} reward(s) for pet {} at level {}", rewards.size(), pet.getDisplayName().getString(), level);
        
        for (woflo.petsplus.api.LevelReward reward : rewards) {
            try {
                reward.apply(pet, petComp, owner, level);
                Petsplus.LOGGER.debug("  - {}", reward.getDescription());
            } catch (Exception e) {
                Petsplus.LOGGER.error("  - Failed to apply {}: {}", reward.getDescription(), e.getMessage(), e);
            }
        }
    }
}

