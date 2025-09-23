package woflo.petsplus.items;

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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

/**
 * Creates and manages "Proof of Existence" memorial items dropped when pets die permanently.
 * These items serve as sentimental mementos containing the pet's details and history.
 * Uses deterministic variety based on pet characteristics for unique but consistent memorial texts.
 */
public class ProofOfExistence {
    
    // Memorial quote pools for variety
    private static final String[] OPENING_QUOTES = {
        "\"was a faithful companion.\"",
        "\"brought joy to every day.\"", 
        "\"was always by your side.\"",
        "\"made the world brighter.\"",
        "\"was truly one of a kind.\"",
        "\"had a heart of gold.\"",
        "\"was loved beyond measure.\"",
        "\"left pawprints on your heart.\"",
        "\"was your greatest adventure.\"",
        "\"shared countless memories.\"",
        "\"was irreplaceable.\"",
        "\"touched so many lives.\"",
        "\"was pure loyalty incarnate.\"",
        "\"brought warmth to cold nights.\"",
        "\"was a beacon of hope.\"",
        "\"made every moment special.\""
    };
    
    private static final String[] CLOSING_EPITAPHS = {
        "\"Gone but not forgotten.\"",
        "\"Forever in our hearts.\"",
        "\"Until we meet again.\"",
        "\"Their spirit lives on.\"",
        "\"Rest well, dear friend.\"",
        "\"May they find peace.\"",
        "\"A life well lived.\"",
        "\"They gave their all.\"",
        "\"Love never dies.\"",
        "\"In memory eternal.\"",
        "\"Their legend continues.\"",
        "\"A bond unbroken.\"",
        "\"Sleep well, warrior.\"",
        "\"The journey continues.\"",
        "\"Always remembered.\"",
        "\"Their story endures.\""
    };
    
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
    
    /**
     * Generate a deterministic but varied memorial text based on pet characteristics.
     */
    private static String generateMemorialQuote(String petName, PetComponent petComp, MobEntity pet) {
        // Use pet UUID and level as seed for deterministic variety
        long seed = pet.getUuid().getMostSignificantBits() ^ (petComp.getLevel() * 31L);
        
        // Select quote components based on seed
        int openingIndex = Math.abs((int) (seed % OPENING_QUOTES.length));
        int epitaphIndex = Math.abs((int) ((seed >> 8) % CLOSING_EPITAPHS.length));
        
        String openingQuote = OPENING_QUOTES[openingIndex];
        String closingEpitaph = CLOSING_EPITAPHS[epitaphIndex];
        
        return "§8" + petName + " " + openingQuote + " " + closingEpitaph;
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
            return resolveMessageString(epithets.get(index), PetRoleType.fallbackName(roleId));
        }
        return PetRoleType.fallbackName(roleId);
    }
    
    /**
     * Generate a level-based descriptor.
     */
    private static String generateLevelDescriptor(int level) {
        // Map level to descriptor tier
        if (level <= 10) {
            return LEVEL_DESCRIPTORS[level - 1]; // 0-9 (Novice tier)
        } else if (level <= 20) {
            return LEVEL_DESCRIPTORS[9 + (level - 11)]; // 10-19 (Experienced tier)
        } else {
            return LEVEL_DESCRIPTORS[19 + Math.min(level - 21, 9)]; // 20-29 (Master tier)
        }
    }
    
    /**
     * Generate closing epitaph based on how the pet died and their achievements.
     */
    private static String generateClosingEpitaph(PetComponent petComp, MobEntity pet) {
        long seed = pet.getUuid().getMostSignificantBits() ^ petComp.getExperience();
        int epitaphIndex = Math.abs((int) (seed % CLOSING_EPITAPHS.length));
        return CLOSING_EPITAPHS[epitaphIndex];
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

            if (milestone >= 20) {
                String summary = resolveMessageString(
                    roleType.presentation().adminSummary(),
                    PetRoleType.fallbackName(roleId)
                );
                if (!summary.isBlank()) {
                    return baseName + " (" + summary + ")";
                }
            }
        }

        if (milestone >= 20) {
            return baseName + " (" + PetRoleType.fallbackName(roleId) + ")";
        }

        return baseName;
    }
    
    /**
     * Create a Proof of Existence item for a deceased pet.
     */
    public static ItemStack createMemorial(MobEntity pet, PetComponent petComp) {
        ItemStack memorial = new ItemStack(Items.PAPER);

        // Set the display name with role epithet
        String petName = pet.hasCustomName() ? pet.getCustomName().getString() : pet.getType().getName().getString();
        String roleEpithet = generateRoleEpithet(petComp, pet);
        memorial.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME,
            Text.literal("§7Proof of Existence: §f" + petName + " §8" + roleEpithet).formatted(Formatting.ITALIC));

        Identifier roleId = petComp.getRoleId();
        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);

        // Create the lore with pet details
        List<Text> lore = new ArrayList<>();

        // Varied memorial quote
        lore.add(Text.literal(generateMemorialQuote(petName, petComp, pet)));
        lore.add(Text.empty());

        // Pet details with descriptive text
        String levelDescriptor = generateLevelDescriptor(petComp.getLevel());
        lore.add(Text.literal("§7Role: §f" + getRoleDisplayName(roleId, roleType)));
        lore.add(Text.literal("§7Rank: §f" + levelDescriptor + " §7(Level " + petComp.getLevel() + ")"));
        lore.add(Text.literal("§7Experience: §f" + formatExperience(petComp.getExperience())));

        // Characteristics if they exist (with more descriptive text)
        if (petComp.getCharacteristics() != null) {
            var chars = petComp.getCharacteristics();
            lore.add(Text.empty());
            lore.add(Text.literal("§7Traits:"));

            // More descriptive characteristic names
            float vitality = chars.getVitalityModifier(roleType);
            float attack = chars.getAttackModifier(roleType);
            float defense = chars.getDefenseModifier(roleType);

            if (Math.abs(vitality) > 0.01f) {
                String vitalityDesc = vitality > 0 ? "Vigorous" : "Delicate";
                lore.add(Text.literal("§8  " + vitalityDesc + ": " + formatModifier(vitality)));
            }
            if (Math.abs(attack) > 0.01f) {
                String attackDesc = attack > 0 ? "Fierce" : "Gentle";
                lore.add(Text.literal("§8  " + attackDesc + ": " + formatModifier(attack)));
            }
            if (Math.abs(defense) > 0.01f) {
                String defenseDesc = defense > 0 ? "Resilient" : "Fragile";
                lore.add(Text.literal("§8  " + defenseDesc + ": " + formatModifier(defense)));
            }
        }
        
        // Milestone achievements with more flavor
        List<Integer> unlockedMilestones = getUnlockedMilestones(petComp);
        if (!unlockedMilestones.isEmpty()) {
            lore.add(Text.empty());
            lore.add(Text.literal("§7Achievements:"));
            for (int milestone : unlockedMilestones) {
                String achievementName = getMilestoneAchievementName(milestone, roleId);
                lore.add(Text.literal("§8  ✓ " + achievementName));
            }
        }

        // Time of death and closing epitaph
        lore.add(Text.empty());
        lore.add(Text.literal("§8Lost on " + getCurrentTimestamp()));
        lore.add(Text.literal(generateClosingEpitaph(petComp, pet)));
        
        // Set the lore using data components
        memorial.set(net.minecraft.component.DataComponentTypes.LORE, 
            new net.minecraft.component.type.LoreComponent(lore));
        
        // Add custom data component to identify this as a PoE item
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
        itemEntity.setPickupDelay(20); // Short delay so owner can pick it up easily
        
        // Give it a gentle upward motion
        itemEntity.setVelocity(0, 0.2, 0);
        
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
        return PetRoleType.fallbackName(roleId);
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

    private static String formatExperience(int exp) {
        if (exp >= 1000000) {
            return String.format("%.1fM", exp / 1000000.0);
        } else if (exp >= 1000) {
            return String.format("%.1fK", exp / 1000.0);
        }
        return String.valueOf(exp);
    }
    
    private static String formatModifier(float modifier) {
        if (modifier > 0) {
            return String.format("§a+%.1f%%", modifier * 100);
        } else if (modifier < 0) {
            return String.format("§c%.1f%%", modifier * 100);
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