package woflo.petsplus.items;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Formatting;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.api.PetRole;
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
    
    private static final String[] ROLE_EPITHETS = {
        // Guardian epithets
        "The Steadfast Shield", "The Loyal Protector", "The Unbreaking Wall", "The Gentle Guardian",
        // Striker epithets  
        "The Swift Strike", "The Final Word", "The Hunter's Mark", "The Decisive Blow",
        // Support epithets
        "The Healing Heart", "The Caring Soul", "The Gentle Mender", "The Life Giver",
        // Scout epithets
        "The Keen Eye", "The Path Finder", "The Bright Lantern", "The Treasure Seeker",
        // Skyrider epithets
        "The Wind Walker", "The Sky Dancer", "The Cloud Rider", "The Storm Caller",
        // Enchantment-Bound epithets
        "The Mystic Echo", "The Arcane Bond", "The Magic Weaver", "The Spell Keeper",
        // Cursed One epithets
        "The Beautiful Curse", "The Dark Blessing", "The Shadowed Light", "The Grim Fortune",
        // Eepy Eeper epithets
        "The Dreaming Spirit", "The Restful Soul", "The Sleepy Guardian", "The Cozy Companion",
        // Eclipsed epithets
        "The Void Touched", "The Shadow Walker", "The Eclipse Born", "The Dark Star"
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
        // Use pet UUID and role for deterministic selection
        long seed = pet.getUuid().getLeastSignificantBits() ^ petComp.getRole().ordinal();
        
        // Get role-specific epithets (4 per role)
        int roleOffset = petComp.getRole().ordinal() * 4;
        int epithetIndex = Math.abs((int) (seed % 4));
        
        return ROLE_EPITHETS[roleOffset + epithetIndex];
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
    private static String getMilestoneAchievementName(int milestone, PetRole role) {
        String baseName = switch (milestone) {
            case 10 -> "First Tribute";
            case 20 -> "Proven Companion"; 
            case 30 -> "Legendary Bond";
            default -> "Level " + milestone;
        };
        
        // Add role-specific suffix for higher milestones
        if (milestone >= 20) {
            String roleSuffix = switch (role) {
                case GUARDIAN -> "Defender";
                case STRIKER -> "Hunter";
                case SUPPORT -> "Healer";
                case SCOUT -> "Explorer";
                case SKYRIDER -> "Sky Walker";
                case ENCHANTMENT_BOUND -> "Spell Weaver";
                case CURSED_ONE -> "Dark Champion";
                case EEPY_EEPER -> "Dream Walker";
                case ECLIPSED -> "Void Touched";
            };
            return baseName + " " + roleSuffix;
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
        
        // Create the lore with pet details
        List<Text> lore = new ArrayList<>();
        
        // Varied memorial quote
        lore.add(Text.literal(generateMemorialQuote(petName, petComp, pet)));
        lore.add(Text.empty());
        
        // Pet details with descriptive text
        String levelDescriptor = generateLevelDescriptor(petComp.getLevel());
        lore.add(Text.literal("§7Role: §f" + getRoleDisplayName(petComp.getRole())));
        lore.add(Text.literal("§7Rank: §f" + levelDescriptor + " §7(Level " + petComp.getLevel() + ")"));
        lore.add(Text.literal("§7Experience: §f" + formatExperience(petComp.getExperience())));
        
        // Characteristics if they exist (with more descriptive text)
        if (petComp.getCharacteristics() != null) {
            var chars = petComp.getCharacteristics();
            lore.add(Text.empty());
            lore.add(Text.literal("§7Traits:"));
            
            // More descriptive characteristic names
            float vitality = chars.getVitalityModifier(petComp.getRole());
            float attack = chars.getAttackModifier(petComp.getRole());
            float defense = chars.getDefenseModifier(petComp.getRole());
            
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
                String achievementName = getMilestoneAchievementName(milestone, petComp.getRole());
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
                petComp.getRole().toString(),
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
    
    private static String getRoleDisplayName(PetRole role) {
        return switch (role) {
            case GUARDIAN -> "Guardian";
            case STRIKER -> "Striker";
            case SUPPORT -> "Support";
            case SCOUT -> "Scout";
            case SKYRIDER -> "Skyrider";
            case ENCHANTMENT_BOUND -> "Enchantment-Bound";
            case CURSED_ONE -> "Cursed One";
            case EEPY_EEPER -> "Eepy Eeper";
            case ECLIPSED -> "Eclipsed";
        };
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