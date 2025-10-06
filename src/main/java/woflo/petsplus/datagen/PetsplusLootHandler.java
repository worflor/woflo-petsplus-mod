package woflo.petsplus.datagen;

import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Handles loot generation for Pets+ events and abilities.
 * Provides utilities for pet death drops, ability rewards, and special item generation.
 */
public class PetsplusLootHandler {
    
    private static final Random random = new Random();
    
    /**
     * Handles drops when a pet dies.
     * Generates mourning items and potential memorial tokens.
     */
    public static void handlePetDeath(MobEntity pet, ServerPlayerEntity owner) {
        if (pet.getWorld().isClient) return;
        
        ServerWorld world = (ServerWorld) pet.getWorld();
        Vec3d dropPos = pet.getPos();
        
        List<ItemStack> drops = generatePetDeathDrops(pet, owner);
        
        for (ItemStack stack : drops) {
            if (!stack.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(world, dropPos.x, dropPos.y, dropPos.z, stack);
                world.spawnEntity(itemEntity);
            }
        }
    }
    
    /**
     * Generates loot drops for pet death events.
     */
    private static List<ItemStack> generatePetDeathDrops(MobEntity pet, ServerPlayerEntity owner) {
        List<ItemStack> drops = new ArrayList<>();
        
        PetComponent petComp = PetComponent.get(pet);
        int petLevel = petComp != null ? petComp.getLevel() : 1;
        
        // 30% chance to drop a bone as a memorial
        if (random.nextFloat() < 0.3f) {
            drops.add(new ItemStack(Items.BONE));
        }
        
        // 10% chance to drop a name tag for remembrance
        if (random.nextFloat() < 0.1f) {
            drops.add(new ItemStack(Items.NAME_TAG));
        }
        
        // 5% chance to drop paper that could become a respec token
        if (random.nextFloat() < 0.05f) {
            drops.add(new ItemStack(Items.PAPER));
        }
        
        // Always drop 1-3 experience bottles as compensation
        int expBottles = 1 + random.nextInt(3);
        drops.add(new ItemStack(Items.EXPERIENCE_BOTTLE, expBottles));
        
        // Higher level pets drop better memorial items
        if (petLevel >= 20) {
            // 15% chance for rare memorial items at level 20+
            if (random.nextFloat() < 0.15f) {
                drops.add(new ItemStack(Items.GOLDEN_APPLE));
            }
        }
        
        if (petLevel >= 30) {
            // 5% chance for very rare memorial items at level 30
            if (random.nextFloat() < 0.05f) {
                drops.add(new ItemStack(Items.ENCHANTED_BOOK));
            }
        }
        
        return drops;
    }
    
    /**
     * Handles loot from Enchanted-Bound mining bonuses.
     */
    public static void handleEnchantedBoundMiningBonus(ServerPlayerEntity player, BlockPos blockPos, ItemStack originalDrop) {
        if (player.getWorld().isClient) return;
        
        ServerWorld world = (ServerWorld) player.getWorld();
        
        // 5% base chance for extra drops
        float bonusChance = 0.05f;
        
        // Higher chance for certain valuable ores
        if (originalDrop.isOf(Items.DIAMOND) || originalDrop.isOf(Items.EMERALD)) {
            bonusChance = 0.15f;
        } else if (originalDrop.isOf(Items.GOLD_INGOT) || originalDrop.isOf(Items.IRON_INGOT)) {
            bonusChance = 0.10f;
        }
        
        if (random.nextFloat() < bonusChance) {
            // Drop a duplicate of the original item
            ItemStack bonusDrop = originalDrop.copy();
            bonusDrop.setCount(1); // Always just one extra
            
            ItemEntity itemEntity = new ItemEntity(world, 
                blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5, 
                bonusDrop);
            world.spawnEntity(itemEntity);
            
            // Visual effect
            world.spawnParticles(net.minecraft.particle.ParticleTypes.ENCHANT,
                blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5,
                5, 0.3, 0.3, 0.3, 0.1);
        }
    }
    
    /**
     * Generates scout exploration rewards.
     */
    public static void handleScoutExplorationReward(ServerPlayerEntity player, Vec3d pos) {
        if (player.getWorld().isClient) return;
        
        ServerWorld world = (ServerWorld) player.getWorld();
        List<ItemStack> rewards = new ArrayList<>();
        
        // 60% chance for torches
        if (random.nextFloat() < 0.6f) {
            int torchCount = 1 + random.nextInt(4);
            rewards.add(new ItemStack(Items.TORCH, torchCount));
        }
        
        // 40% chance for arrows
        if (random.nextFloat() < 0.4f) {
            int arrowCount = 2 + random.nextInt(7);
            rewards.add(new ItemStack(Items.ARROW, arrowCount));
        }
        
        // 30% chance for bread
        if (random.nextFloat() < 0.3f) {
            int breadCount = 1 + random.nextInt(2);
            rewards.add(new ItemStack(Items.BREAD, breadCount));
        }
        
        for (ItemStack reward : rewards) {
            if (!reward.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(world, pos.x, pos.y, pos.z, reward);
                world.spawnEntity(itemEntity);
            }
        }
    }
    
    /**
     * Generates support healing ingredient rewards.
     */
    public static void handleSupportHealingReward(ServerPlayerEntity player, Vec3d pos) {
        if (player.getWorld().isClient) return;
        
        ServerWorld world = (ServerWorld) player.getWorld();
        
        // Chance for various healing ingredients
        if (random.nextFloat() < 0.3f) {
            ItemEntity itemEntity = new ItemEntity(world, pos.x, pos.y, pos.z, 
                new ItemStack(Items.GOLDEN_CARROT));
            world.spawnEntity(itemEntity);
        } else if (random.nextFloat() < 0.2f) {
            ItemEntity itemEntity = new ItemEntity(world, pos.x, pos.y, pos.z, 
                new ItemStack(Items.GLISTERING_MELON_SLICE));
            world.spawnEntity(itemEntity);
        } else if (random.nextFloat() < 0.05f) {
            ItemEntity itemEntity = new ItemEntity(world, pos.x, pos.y, pos.z, 
                new ItemStack(Items.GHAST_TEAR));
            world.spawnEntity(itemEntity);
        }
    }
    
    /**
     * Generates cursed one dark magic rewards.
     */
    public static void handleCursedOneReward(ServerPlayerEntity player, Vec3d pos) {
        if (player.getWorld().isClient) return;
        
        ServerWorld world = (ServerWorld) player.getWorld();
        List<ItemStack> rewards = new ArrayList<>();
        
        // 50% chance for bones
        if (random.nextFloat() < 0.5f) {
            int boneCount = 1 + random.nextInt(3);
            rewards.add(new ItemStack(Items.BONE, boneCount));
        }
        
        // 40% chance for rotten flesh
        if (random.nextFloat() < 0.4f) {
            int fleshCount = 1 + random.nextInt(2);
            rewards.add(new ItemStack(Items.ROTTEN_FLESH, fleshCount));
        }
        
        // 10% chance for phantom membrane
        if (random.nextFloat() < 0.1f) {
            rewards.add(new ItemStack(Items.PHANTOM_MEMBRANE));
        }
        
        // 1% chance for wither skeleton skull
        if (random.nextFloat() < 0.01f) {
            rewards.add(new ItemStack(Items.WITHER_SKELETON_SKULL));
        }
        
        for (ItemStack reward : rewards) {
            if (!reward.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(world, pos.x, pos.y, pos.z, reward);
                world.spawnEntity(itemEntity);
            }
        }
    }
    
    /**
     * Generates milestone celebration rewards.
     */
    public static void handleMilestoneCelebration(ServerPlayerEntity player, int milestone) {
        if (player.getWorld().isClient) return;
        
        ServerWorld world = (ServerWorld) player.getWorld();
        Vec3d pos = player.getPos();
        
        // Fireworks for celebration
        int fireworkCount = 3 + random.nextInt(6);
        ItemStack fireworks = new ItemStack(Items.FIREWORK_ROCKET, fireworkCount);
        
        ItemEntity fireworkEntity = new ItemEntity(world, pos.x, pos.y, pos.z, fireworks);
        world.spawnEntity(fireworkEntity);
        
        // 50% chance for cake
        if (random.nextFloat() < 0.5f) {
            ItemEntity cakeEntity = new ItemEntity(world, pos.x, pos.y, pos.z, 
                new ItemStack(Items.CAKE));
            world.spawnEntity(cakeEntity);
        }
        
        // Experience bottles based on milestone
        int expBottles = Math.max(5, milestone / 3);
        expBottles += random.nextInt(10);
        
        ItemStack expBottleStack = new ItemStack(Items.EXPERIENCE_BOTTLE, expBottles);
        ItemEntity expEntity = new ItemEntity(world, pos.x, pos.y, pos.z, expBottleStack);
        world.spawnEntity(expEntity);
    }
    
    /**
     * Generates admin testing kit items.
     */
    public static void giveAdminTestingKit(ServerPlayerEntity player) {
        // Tribute items
        player.getInventory().insertStack(new ItemStack(Items.GOLD_INGOT, 5));
        player.getInventory().insertStack(new ItemStack(Items.DIAMOND, 3));
        player.getInventory().insertStack(new ItemStack(Items.NETHERITE_INGOT, 1));
        
        // Testing materials
        player.getInventory().insertStack(new ItemStack(Items.NAME_TAG, 2));
        player.getInventory().insertStack(new ItemStack(Items.BONE, 10));
    }
}