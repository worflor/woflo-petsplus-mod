package woflo.petsplus.items;

import woflo.petsplus.api.registry.RoleIdentifierUtil;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.component.PetsplusComponents;
import woflo.petsplus.history.HistoryEvent;
import woflo.petsplus.stats.PetImprint;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Creates and manages "Proof of Existence" memorial items dropped when pets die permanently.
 * These items serve as sentimental mementos containing the pet's details and history.
 * Dynamically generates content from pet's actual journey - no hardcoded text pools.
 */
public class ProofOfExistence {
    
    // Constants for magic numbers
    private static final int LEVEL_NOVICE_MAX = 10;
    private static final int LEVEL_EXPERIENCED_MAX = 20;
    private static final int LEVEL_DESCRIPTOR_MASTER_OFFSET = 19;
    private static final float MODIFIER_THRESHOLD = 0.01f;
    private static final float MODIFIER_MULTIPLIER = 100.0f;
    private static final int PICKUP_DELAY_TICKS = 20;
    private static final double UPWARD_VELOCITY = 0.2;
    private static final int TICKS_PER_DAY = 24000; // Minecraft day
    private static final int TICKS_PER_HOUR = 1000; // Minecraft hour
    private static final int MAX_JOURNEY_HIGHLIGHTS = 3;
    
    // Level descriptors for rank display
    private static final String[] LEVEL_DESCRIPTORS = {
        // 1-10: Novice tier
        "Novice", "Apprentice", "Trainee", "Student", "Learner",
        "Beginner", "Recruit", "Initiate", "Fledgling", "Aspirant",
        // 11-20: Experienced tier  
        "Seasoned", "Skilled", "Experienced", "Adept", "Competent",
        "Capable", "Practiced", "Proficient", "Accomplished", "Veteran",
        // 21-30: Master tier
        "Master", "Expert", "Champion", "Elite", "Legendary",
        "Heroic", "Mythic", "Supreme", "Ultimate", "Transcendent"
    };
    
    // ===== DATA ANALYSIS RECORDS =====
    
    /**
     * Analyzed history data from a pet's journey.
     */
    private record HistoryAnalysis(
        int combatVictories,
        int combatDefeats,
        int tradeCount,
        String currentOwner,
        List<String> previousOwners,
        int uniqueOwners,
        int roleChanges,
        List<String> memorableMoments,
        long ticksAlive,
        boolean hasOrNot,
        boolean hasBestFriendForeverer
    ) {
        static HistoryAnalysis analyze(PetComponent petComp, long deathTick) {
            List<HistoryEvent> history = petComp.getHistory();
            
            int victories = 0;
            int defeats = 0;
            int trades = 0;
            String currentOwner = null;
            List<String> previousOwners = new ArrayList<>();
            Set<UUID> owners = new HashSet<>();
            int roleChanges = 0;
            List<String> moments = new ArrayList<>();
            long tamedTick = -1;
            boolean hasOrNot = false;
            boolean hasBestFriendForeverer = false;
            
            // Get most recent owner (at death)
            if (!history.isEmpty()) {
                // History is ordered chronologically, so last event has current owner
                HistoryEvent lastEvent = history.get(history.size() - 1);
                if (lastEvent != null && lastEvent.ownerName() != null) {
                    currentOwner = lastEvent.ownerName();
                }
            }
            
            for (HistoryEvent event : history) {
                owners.add(event.ownerUuid());
                
                switch (event.eventType()) {
                    case HistoryEvent.EventType.COMBAT -> {
                        if (event.eventData().contains("\"result\":\"victory\"")) victories++;
                        else if (event.eventData().contains("\"result\":\"defeat\"")) defeats++;
                    }
                    case HistoryEvent.EventType.TRADE -> {
                        trades++;
                        String fromName = extractName(event.eventData(), "from_name");
                        String toName = extractName(event.eventData(), "to_name");
                        
                        // Track previous owners
                        if (fromName != null && !fromName.equals("Unknown") && !previousOwners.contains(fromName)) {
                            previousOwners.add(fromName);
                        }
                        
                        // Add to memorable moments
                        if (toName != null) {
                            moments.add("Traded to " + toName);
                        }
                    }
                    case HistoryEvent.EventType.ROLE_CHANGE -> {
                        roleChanges++;
                        if (event.eventData().contains("\"to\":")) {
                            String toRole = extractValue(event.eventData(), "to");
                            if (toRole != null && !toRole.isEmpty()) {
                                moments.add("Became " + toRole);
                            }
                        }
                    }
                    case HistoryEvent.EventType.ACHIEVEMENT -> {
                        String achievementType = extractValue(event.eventData(), "achievement_type");
                        if (achievementType != null) {
                            if (achievementType.equals(HistoryEvent.AchievementType.OR_NOT)) {
                                hasOrNot = true;
                            } else if (achievementType.equals(HistoryEvent.AchievementType.BEST_FRIEND_FOREVERER)) {
                                hasBestFriendForeverer = true;
                            }
                        }
                        String achievement = extractAchievementName(event.eventData());
                        if (achievement != null && !achievement.isEmpty()) {
                            moments.add(achievement);
                        }
                    }
                    case HistoryEvent.EventType.OWNERSHIP_START -> {
                        if (tamedTick == -1) {
                            tamedTick = event.timestamp();
                        }
                    }
                    case HistoryEvent.EventType.MOOD_MILESTONE -> {
                        String mood = extractValue(event.eventData(), "mood");
                        if (mood != null && !mood.isEmpty()) {
                            // Format mood nicely
                            String formattedMood = mood.substring(0, 1).toUpperCase() + mood.substring(1).toLowerCase();
                            moments.add("Felt " + formattedMood);
                        }
                    }
                }
            }
            
            // Remove current owner from previous owners list
            previousOwners.remove(currentOwner);
            
            long ticksAlive = tamedTick > 0 ? (deathTick - tamedTick) : -1;
            
            return new HistoryAnalysis(
                victories, defeats, trades, currentOwner, previousOwners,
                owners.size(), roleChanges, moments, ticksAlive,
                hasOrNot, hasBestFriendForeverer
            );
        }
    }
    
    /**
     * Emotional state snapshot at death.
     */
    private record EmotionSnapshot(
        @Nullable PetComponent.Emotion dominantEmotion,
        @Nullable PetComponent.Emotion natureMajor,
        @Nullable PetComponent.Emotion natureMinor,
        PetComponent.Mood currentMood
    ) {
        static EmotionSnapshot capture(PetComponent petComp) {
            PetComponent.Emotion dominant = petComp.getDominantEmotion();
            PetComponent.NatureEmotionProfile nature = petComp.getNatureEmotionProfile();
            PetComponent.Mood mood = petComp.getCurrentMood();
            
            return new EmotionSnapshot(
                dominant,
                nature.majorEmotion(),
                nature.minorEmotion(),
                mood
            );
        }
        
        boolean hasData() {
            return dominantEmotion != null || natureMajor != null;
        }
    }
    
    // ===== HELPER METHODS =====
    
    private static String extractName(String json, String key) {
        if (json == null || json.isEmpty() || key == null || key.isEmpty()) return "Unknown";
        
        int keyIndex = json.indexOf("\"" + key + "\":");
        if (keyIndex == -1) return "Unknown";
        int valueStart = json.indexOf("\"", keyIndex + key.length() + 3);
        if (valueStart == -1) return "Unknown";
        int valueEnd = json.indexOf("\"", valueStart + 1);
        if (valueEnd == -1 || valueEnd <= valueStart) return "Unknown";
        
        String extracted = json.substring(valueStart + 1, valueEnd);
        return extracted.isEmpty() ? "Unknown" : extracted;
    }
    
    private static String extractValue(String json, String key) {
        if (json == null || json.isEmpty() || key == null || key.isEmpty()) return null;
        
        int keyIndex = json.indexOf("\"" + key + "\":");
        if (keyIndex == -1) return null;
        int valueStart = json.indexOf("\"", keyIndex + key.length() + 3);
        if (valueStart == -1) return null;
        int valueEnd = json.indexOf("\"", valueStart + 1);
        if (valueEnd == -1 || valueEnd <= valueStart) return null;
        
        String extracted = json.substring(valueStart + 1, valueEnd);
        return extracted.isEmpty() ? null : extracted;
    }
    
    private static String extractAchievementName(String json) {
        if (json == null || json.isEmpty()) return null;
        
        String type = extractValue(json, "achievement_type");
        if (type == null || type.isEmpty()) return null;
        
        return switch (type) {
            case HistoryEvent.AchievementType.DREAM_ESCAPE -> "Escaped death's dream";
            case HistoryEvent.AchievementType.PET_SACRIFICE -> "Sacrificed for their owner";
            case HistoryEvent.AchievementType.GUARDIAN_PROTECTION -> "Protected their owner";
            case HistoryEvent.AchievementType.BEST_FRIEND_FOREVERER -> "Forged an unbreakable bond";
            case HistoryEvent.AchievementType.OR_NOT -> "Best friends foreverer... or not.";
            default -> {
                // Try to make unknown achievements at least readable
                String readable = type.replace("_", " ").toLowerCase();
                yield "Achieved: " + readable;
            }
        };
    }
    
    private static String formatTimeAlive(long ticks) {
        if (ticks <= 0) return null;
        
        int days = (int) (ticks / TICKS_PER_DAY);
        long remainingTicks = ticks % TICKS_PER_DAY;
        int hours = (int) (remainingTicks / TICKS_PER_HOUR);
        
        // Legendary time spans (100+ days) in gold
        if (days >= 100) {
            return "§6an epoch together§r";
        }
        
        // Very long (60+ days)
        if (days >= 60) {
            return "§fa lifetime of memories§r";
        }
        
        // Long (30+ days)
        if (days >= 30) {
            return "§fan age together§r";
        }
        
        // Medium to long (7+ days)
        if (days >= 7) {
            String descriptor = days >= 14 ? "of devotion" : "of companionship";
            return "§f" + days + " days " + descriptor + "§r";
        }
        
        // Standard format for 1-6 days
        if (days > 0 && hours > 0) {
            return "§f" + days + " day" + (days != 1 ? "s" : "") + ", " + hours + " hour" + (hours != 1 ? "s" : "") + "§r";
        } else if (days > 0) {
            return "§f" + days + " day" + (days != 1 ? "s" : "") + "§r";
        }
        
        // Short time spans (hours)
        if (hours >= 5) {
            return "§fa handful of hours§r";
        } else if (hours > 0) {
            return "§fbrief hours§r";
        }
        
        // Very short (less than 1 hour) - context-aware sadness
        return "§8a fleeting moment§r";
    }
    
    private static List<String> selectMemorableMomentsByPriority(List<String> allMoments, long seed, int max) {
        if (allMoments == null || allMoments.isEmpty()) return List.of();
        
        // Remove duplicates while preserving order
        List<String> uniqueMoments = new ArrayList<>(new LinkedHashSet<>(allMoments));
        
        // Tiered priority sorting (higher priority = shown first)
        Map<String, Integer> priorityMap = new HashMap<>();
        for (String moment : uniqueMoments) {
            int priority = 0;
            
            // Tier 3: OR_NOT (ultra-rare) - shown in dedicated section, but track for sorting
            if (moment.contains("or not")) {
                priority = 1000;
            }
            // Tier 2: Rare achievements
            else if (moment.contains("Escaped") || moment.contains("Sacrificed") || 
                     moment.contains("unbreakable bond")) {
                priority = 100;
            }
            // Tier 1: Notable achievements
            else if (moment.contains("Protected") || moment.contains("Achieved")) {
                priority = 50;
            }
            // Tier 0: Standard events (trades, roles, moods)
            else {
                priority = 10;
            }
            
            priorityMap.put(moment, priority);
        }
        
        // Sort by priority (high to low), then deterministically shuffle within same priority
        uniqueMoments.sort((a, b) -> {
            int priorityDiff = priorityMap.get(b) - priorityMap.get(a);
            if (priorityDiff != 0) return priorityDiff;
            // Deterministic tie-breaking using hash + seed
            return Integer.compare(
                (a.hashCode() ^ Long.hashCode(seed)) & 0x7FFFFFFF,
                (b.hashCode() ^ Long.hashCode(seed)) & 0x7FFFFFFF
            );
        });
        
        return uniqueMoments.subList(0, Math.min(max, uniqueMoments.size()));
    }
    
    /**
     * Generate memorial opening text based on pet's actual journey.
     */
    private static String generateMemorialOpening(String petName, HistoryAnalysis history, long seed) {
        if (petName == null || petName.isEmpty()) {
            petName = "They";
        }
        
        List<String> phrases = new ArrayList<>();
        Random random = new Random(seed);
        
        // Build phrases from actual history with variety
        if (history.combatVictories >= 50) {
            // Legendary combat - gold color
            String[] options = {
                "§6fought through " + history.combatVictories + " battles§r",
                "§6proved themselves in " + history.combatVictories + " conflicts§r",
                "§6emerged victorious " + history.combatVictories + " times§r"
            };
            phrases.add(options[random.nextInt(options.length)]);
        } else if (history.combatVictories > 20) {
            // Impressive combat - yellow color
            String[] options = {
                "§efought through " + history.combatVictories + " battles§r",
                "§eproved themselves in " + history.combatVictories + " conflicts§r",
                "§eemerged victorious " + history.combatVictories + " times§r"
            };
            phrases.add(options[random.nextInt(options.length)]);
        } else if (history.combatVictories > 5) {
            String[] options = {
                "fought through " + history.combatVictories + " battles",
                "stood strong in combat",
                "defended their companion"
            };
            phrases.add(options[random.nextInt(options.length)]);
        } else if (history.combatVictories > 0) {
            String[] options = {
                "stood firm in battle",
                "faced danger bravely",
                "answered the call to fight"
            };
            phrases.add(options[random.nextInt(options.length)]);
        }
        
        if (history.tradeCount > 2) {
            phrases.add("journeyed between " + history.uniqueOwners + " companion" + (history.uniqueOwners != 1 ? "s" : ""));
        } else if (history.tradeCount > 0) {
            String[] options = {
                "found new companions",
                "embraced a new bond",
                "shared their loyalty"
            };
            phrases.add(options[random.nextInt(options.length)]);
        } else if (history.currentOwner != null) {
            String[] options = {
                "stayed loyal to " + history.currentOwner,
                "remained steadfast with " + history.currentOwner,
                "never left " + history.currentOwner + "'s side"
            };
            phrases.add(options[random.nextInt(options.length)]);
        }
        
        if (history.roleChanges > 0) {
            String[] options = {
                "embraced new paths",
                "adapted to new purposes",
                "evolved through change"
            };
            phrases.add(options[random.nextInt(options.length)]);
        }
        
        // If no history, use simple but varied statements
        if (phrases.isEmpty()) {
            String[] options = {
                "walked this world.",
                "lived their brief time.",
                "existed, if only for a moment.",
                "was here."
            };
            return "§8" + petName + " " + options[random.nextInt(options.length)];
        }
        
        // Deterministically select 1-2 phrases
        Collections.shuffle(phrases, random);
        int count = Math.min(2, phrases.size());
        
        String combined = phrases.stream().limit(count).collect(Collectors.joining(" and "));
        return "§8" + petName + " " + combined + ".";
    }
    
    /**
     * Generate a role-based epithet for the pet.
     */
    private static String generateRoleEpithet(PetComponent petComp, MobEntity pet) {
        Identifier roleId = petComp.getRoleId();
        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
        if (roleType != null && roleType.presentation().hasMemorialEpithets()) {
            List<PetRoleType.Message> epithets = roleType.presentation().memorialEpithets();
            long seed = pet.getUuid().getLeastSignificantBits() ^ pet.getUuid().getMostSignificantBits();
            int index = Math.floorMod((int) seed, epithets.size());
            return resolveMessageString(epithets.get(index), RoleIdentifierUtil.formatName(roleId));
        }
        return RoleIdentifierUtil.formatName(roleId);
    }
    
    /**
     * Generate a level-based descriptor.
     */
    private static String generateLevelDescriptor(int level) {
        // Clamp level to valid range
        level = Math.max(1, Math.min(30, level));
        
        // Map level to descriptor tier
        if (level <= LEVEL_NOVICE_MAX) {
            return LEVEL_DESCRIPTORS[level - 1]; // 1-10 (Novice tier)
        } else if (level <= LEVEL_EXPERIENCED_MAX) {
            return LEVEL_DESCRIPTORS[9 + (level - 11)]; // 11-20 (Experienced tier)
        } else {
            return LEVEL_DESCRIPTORS[LEVEL_DESCRIPTOR_MASTER_OFFSET + Math.min(level - 21, 9)]; // 21-30 (Master tier)
        }
    }
    
    /**
     * Generate closing epitaph based on pet's essence and journey.
     */
    private static String generateClosingEpitaph(HistoryAnalysis history, EmotionSnapshot emotion, long seed, boolean isLegendary) {
        List<String> epitaphs = new ArrayList<>();
        
        // OR_NOT-specific epitaphs (ultra-rare tragedy)
        if (history.hasOrNot) {
            epitaphs.add("Forever ended too soon.");
            epitaphs.add("The first, the last, the legend.");
            epitaphs.add("The bond was unbreakable. Until it broke.");
            epitaphs.add("'Foreverer' was a promise unkept.");
            epitaphs.add("First to bond, first to fall.");
            epitaphs.add("The irony is not lost.");
            epitaphs.add("A legacy written in bitter irony.");
        }
        
        // Generate epitaphs from actual emotional data
        if (emotion.hasData()) {
            if (emotion.dominantEmotion != null) {
                epitaphs.add("Their " + formatEmotionName(emotion.dominantEmotion) + " spirit endures.");
                epitaphs.add("A " + formatEmotionName(emotion.dominantEmotion).toLowerCase() + " soul forever.");
            }
            if (emotion.natureMajor != null && emotion.natureMajor != emotion.dominantEmotion) {
                epitaphs.add("Forever " + formatEmotionName(emotion.natureMajor).toLowerCase() + ".");
                epitaphs.add("Their " + formatEmotionName(emotion.natureMajor).toLowerCase() + " nature remembered.");
            }
        }
        
        // Combat-based epitaphs
        if (history.combatVictories > 20) {
            epitaphs.add("A legend's rest well-earned.");
            epitaphs.add("Their battles won, their watch ended.");
            epitaphs.add("Victory was theirs, now peace.");
        } else if (history.combatVictories > 10) {
            epitaphs.add("A warrior's rest well-earned.");
            epitaphs.add("They fought well.");
            epitaphs.add("Valiant to the end.");
        } else if (history.combatVictories > 0) {
            epitaphs.add("Brave until the last.");
            epitaphs.add("Courage remembered.");
        }
        
        // Journey-based epitaphs
        if (history.uniqueOwners > 2) {
            epitaphs.add("Many hearts remember.");
            epitaphs.add("Loved by many.");
            epitaphs.add("They touched many lives.");
        } else if (history.uniqueOwners > 1) {
            epitaphs.add("Two hearts remember.");
            epitaphs.add("Shared between companions.");
        }
        
        if (history.roleChanges > 0) {
            epitaphs.add("Adaptable and true.");
            epitaphs.add("Changed, but never broken.");
        }
        
        // Universal meaningful epitaphs (always available)
        epitaphs.add("Gone, but the bond remains.");
        epitaphs.add("Their story lives on.");
        epitaphs.add("Forever in memory.");
        epitaphs.add("The journey ends, the legend begins.");
        epitaphs.add("Until we meet again.");
        epitaphs.add("Rest now, faithful one.");
        epitaphs.add("Their legacy endures.");
        epitaphs.add("A bond unbroken by time.");
        epitaphs.add("They mattered.");
        epitaphs.add("Never forgotten.");
        
        // Deterministic selection
        Random random = new Random(seed);
        String epitaph = epitaphs.get(random.nextInt(epitaphs.size()));
        
        // Use gold for legendary pets
        return isLegendary ? "§6" + epitaph + "§r" : "§8" + epitaph;
    }
    
    /**
     * Check if pet qualifies as legendary (for gold coloring).
     */
    private static boolean isLegendaryPet(HistoryAnalysis history, PetComponent petComp) {
        int days = (int) (history.ticksAlive / TICKS_PER_DAY);
        // OR_NOT pets are automatically legendary - they were the first to 30, and the first to fall
        return history.hasOrNot || petComp.getLevel() >= 30 || history.combatVictories >= 50 || days >= 100;
    }
    
    private static String formatEmotionName(PetComponent.Emotion emotion) {
        if (emotion == null) return "Unknown";
        
        // Convert enum name to readable format (e.g., KEFI -> Kefi)
        String name = emotion.name();
        if (name.isEmpty()) return "Unknown";
        
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }
    
    /**
     * Get achievement name for milestone levels with role-specific flavor.
     */
    private static String getMilestoneAchievementName(int milestone, Identifier roleId) {
        String baseName = switch (milestone) {
            case 10 -> "First Tribute";
            case 20 -> "Proven Companion";
            case 30 -> "Legendary Bond";
            default -> "Level " + milestone;
        };

        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
        if (roleType != null) {
            for (PetRoleType.MilestoneAdvancement advancement : roleType.milestoneAdvancements()) {
                if (advancement.level() == milestone && advancement.message().isPresent()) {
                    return resolveMessageString(advancement.message(), baseName);
                }
            }

            if (milestone >= LEVEL_EXPERIENCED_MAX) {
                String summary = resolveMessageString(
                    roleType.presentation().adminSummary(),
                    RoleIdentifierUtil.formatName(roleId)
                );
                if (!summary.isBlank()) {
                    return baseName + " (" + summary + ")";
                }
            }
        }

        if (milestone >= LEVEL_EXPERIENCED_MAX) {
            return baseName + " (" + RoleIdentifierUtil.formatName(roleId) + ")";
        }

        return baseName;
    }
    
    /**
     * Create a Proof of Existence item for a deceased pet.
     * Dynamically generates content from the pet's actual journey.
     */
    public static ItemStack createMemorial(MobEntity pet, PetComponent petComp) {
        ItemStack memorial = new ItemStack(Items.PAPER);
        
        // Basic info
        String petName = pet.hasCustomName() ? pet.getCustomName().getString() : pet.getType().getName().getString();
        String roleEpithet = generateRoleEpithet(petComp, pet);
        Identifier roleId = petComp.getRoleId();
        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
        long seed = pet.getUuid().getMostSignificantBits() ^ pet.getUuid().getLeastSignificantBits();
        long deathTick = pet.getWorld().getTime();
        
        // Analyze history and capture emotional state
        HistoryAnalysis history = HistoryAnalysis.analyze(petComp, deathTick);
        EmotionSnapshot emotion = EmotionSnapshot.capture(petComp);

        // Set display name
        memorial.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
            Text.literal("§7Proof of Existence: §f§l" + petName + "§r §8" + roleEpithet).formatted(Formatting.ITALIC));

        // Build lore dynamically based on available data
        List<Text> lore = new ArrayList<>();

        // Memorial opening (derived from actual journey) - italicized for gravitas
        lore.add(Text.literal("§o" + generateMemorialOpening(petName, history, seed) + "§r"));
        lore.add(Text.empty());

        // Achievement section (only for OR_NOT - ultra-rare, once-per-player tragedy)
        if (history.hasOrNot) {
            lore.add(Text.literal("§cAchievement:"));
            lore.add(Text.literal("§8  ✦ §c\"Best friends foreverer... or not.\"§r"));
            lore.add(Text.empty());
        }

        // Life summary (always show)
        String levelDescriptor = generateLevelDescriptor(petComp.getLevel());
        lore.add(Text.literal("§7Role: §f" + getRoleDisplayName(roleId, roleType)));
        
        // Add gold color for legendary level (30)
        String levelText = petComp.getLevel() == 30 ? "§6" + levelDescriptor + "§r" : "§f" + levelDescriptor;
        lore.add(Text.literal("§7Rank: " + levelText + " §8(§7Level " + petComp.getLevel() + "§8)"));
        
        // Time alive (if available) - with context-aware personality
        String timeAlive = formatTimeAlive(history.ticksAlive);
        if (timeAlive != null && history.currentOwner != null) {
            lore.add(Text.literal("§7Lived: " + timeAlive + " §7with §f" + history.currentOwner));
        } else if (timeAlive != null) {
            lore.add(Text.literal("§7Lived: " + timeAlive));
        }
        
        // Show previous owners if pet was traded (dark gray - it's past history)
        if (!history.previousOwners.isEmpty()) {
            String ownerList = String.join(", ", history.previousOwners);
            lore.add(Text.literal("§7Previously: §8" + ownerList));
        }

        // Journey highlights (if history exists)
        if (!history.memorableMoments.isEmpty()) {
            lore.add(Text.empty());
            lore.add(Text.literal("§7Journey:"));
            List<String> selected = selectMemorableMomentsByPriority(history.memorableMoments, seed, MAX_JOURNEY_HIGHLIGHTS);
            for (String moment : selected) {
                // Tiered priority system for achievements
                boolean isOrNot = moment.contains("or not");
                boolean isRareAchievement = moment.contains("Escaped") || moment.contains("Sacrificed") || 
                                           moment.contains("unbreakable bond");
                boolean isNotableAchievement = moment.contains("Protected") || moment.contains("Achieved");
                
                // OR_NOT shown in dedicated section, skip here
                if (isOrNot) continue;
                
                String symbol;
                String color;
                if (isRareAchievement) {
                    symbol = "✦";
                    color = "§e";
                } else if (isNotableAchievement) {
                    symbol = "✓";
                    color = "§e";
                } else {
                    symbol = "•";
                    color = "§f";
                }
                
                lore.add(Text.literal("§8  " + symbol + " " + color + moment + "§r"));
            }
        }

        // Essence (inherent nature/personality - if exists)
        if (emotion.natureMajor != null) {
            lore.add(Text.empty());
            lore.add(Text.literal("§bEssence:"));
            
            // Show nature (personality)
            String natureText = "§8  Nature: §f" + formatEmotionName(emotion.natureMajor);
            
            // Add minor nature if significantly different
            if (emotion.natureMinor != null && emotion.natureMinor != emotion.natureMajor) {
                natureText += " §8• §f" + formatEmotionName(emotion.natureMinor);
            }
            
            lore.add(Text.literal(natureText));
        }

        // Traits (only if imprint exists and is notable)
        PetImprint imprint = petComp.getImprint();
        if (imprint != null) {
            float vitality = imprint.getVitalityMultiplier() - 1.0f; // Convert 1.08x → 0.08
            float attack = imprint.getAttackMultiplier() - 1.0f;
            float defense = imprint.getDefenseMultiplier() - 1.0f;
            
            boolean hasNotableTraits = Math.abs(vitality) > MODIFIER_THRESHOLD ||
                                      Math.abs(attack) > MODIFIER_THRESHOLD ||
                                      Math.abs(defense) > MODIFIER_THRESHOLD;
            
            if (hasNotableTraits) {
                lore.add(Text.empty());
                lore.add(Text.literal("§7Traits:"));

                if (Math.abs(vitality) > MODIFIER_THRESHOLD) {
                    String desc = vitality > 0 ? "Vigorous" : "Delicate";
                    lore.add(Text.literal("§8  " + desc + ": " + formatModifier(vitality)));
                }
                if (Math.abs(attack) > MODIFIER_THRESHOLD) {
                    String desc = attack > 0 ? "Fierce" : "Gentle";
                    lore.add(Text.literal("§8  " + desc + ": " + formatModifier(attack)));
                }
                if (Math.abs(defense) > MODIFIER_THRESHOLD) {
                    String desc = defense > 0 ? "Resilient" : "Fragile";
                    lore.add(Text.literal("§8  " + desc + ": " + formatModifier(defense)));
                }
            }
        }
        
        // Bonds & Achievements (milestones)
        List<Integer> unlockedMilestones = getUnlockedMilestones(petComp);
        if (!unlockedMilestones.isEmpty()) {
            lore.add(Text.empty());
            lore.add(Text.literal("§7Bonds:"));
            for (int milestone : unlockedMilestones) {
                String achievementName = getMilestoneAchievementName(milestone, roleId);
                lore.add(Text.literal("§8  ✓ §e" + achievementName + "§r"));
            }
        }

        // Final words
        lore.add(Text.empty());
        lore.add(Text.literal("§8Lost on " + getCurrentTimestamp()));
        
        // Generate epitaph with legendary detection
        boolean isLegendary = isLegendaryPet(history, petComp);
        lore.add(Text.literal(generateClosingEpitaph(history, emotion, seed, isLegendary)));
        
        // Set the lore
        memorial.set(net.minecraft.component.DataComponentTypes.LORE, 
            new net.minecraft.component.type.LoreComponent(lore));
        
        // Add custom data component
        memorial.set(PetsplusComponents.POE_MEMORIAL,
            new PetsplusComponents.PoeData(
                petName,
                pet.getType().toString(),
                roleId.toString(),
                petComp.getLevel(),
                petComp.getExperience(),
                getCurrentTimestamp()
            ));
        
        return memorial;
    }
    
    /**
     * Drop a Proof of Existence memorial at the pet's death location.
     */
    public static void dropMemorial(MobEntity pet, PetComponent petComp, ServerWorld world) {
        ItemStack memorial = createMemorial(pet, petComp);
        
        // Create item entity at pet's location
        Vec3d pos = pet.getPos();
        ItemEntity itemEntity = new ItemEntity(world, pos.x, pos.y, pos.z, memorial);
        itemEntity.setPickupDelay(PICKUP_DELAY_TICKS); // Short delay so owner can pick it up easily
        
        // Give it a gentle upward motion
        itemEntity.setVelocity(0, UPWARD_VELOCITY, 0);
        
        world.spawnEntity(itemEntity);
    }
    
    /**
     * Check if an item stack is a Proof of Existence memorial.
     */
    public static boolean isProofOfExistence(ItemStack stack) {
        return stack.getItem() == Items.PAPER && 
               stack.get(PetsplusComponents.POE_MEMORIAL) != null;
    }
    
    /**
     * Get the pet name from a Proof of Existence item.
     */
    public static String getPetNameFromMemorial(ItemStack stack) {
        if (!isProofOfExistence(stack)) return null;
        PetsplusComponents.PoeData poeData = stack.get(PetsplusComponents.POE_MEMORIAL);
        return poeData != null ? poeData.petName() : null;
    }
    
    private static String getRoleDisplayName(Identifier roleId, @Nullable PetRoleType roleType) {
        if (roleType != null) {
            String translated = Text.translatable(roleType.translationKey()).getString();
            if (!translated.equals(roleType.translationKey())) {
                return translated;
            }
        }
        return RoleIdentifierUtil.formatName(roleId);
    }

    private static String resolveMessageString(PetRoleType.Message message, String fallback) {
        if (message != null) {
            String key = message.translationKey();
            String fallbackText = message.fallback();
            if (key != null && !key.isBlank()) {
                if (Language.getInstance().hasTranslation(key)) {
                    return Text.translatable(key).getString();
                }
                if (fallbackText != null && !fallbackText.isBlank()) {
                    return fallbackText;
                }
                return Text.translatable(key).getString();
            }
            if (fallbackText != null && !fallbackText.isBlank()) {
                return fallbackText;
            }
        }
        return fallback == null ? "" : fallback;
    }
    
    private static String formatModifier(float modifier) {
        if (modifier > 0) {
            return String.format("§a+%.1f%%", modifier * MODIFIER_MULTIPLIER);
        } else if (modifier < 0) {
            return String.format("§c%.1f%%", modifier * MODIFIER_MULTIPLIER);
        } else {
            return "§7±0.0%";
        }
    }
    
    private static List<Integer> getUnlockedMilestones(PetComponent petComp) {
        List<Integer> milestones = new ArrayList<>();
        for (int level : List.of(10, 20, 30)) {
            if (petComp.isMilestoneUnlocked(level)) {
                milestones.add(level);
            }
        }
        return milestones;
    }
    
    private static String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
    }
}