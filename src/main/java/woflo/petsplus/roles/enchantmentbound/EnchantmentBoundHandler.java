package woflo.petsplus.roles.enchantmentbound;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.block.Block;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.PlayerTickListener;
import woflo.petsplus.state.PetSwarmIndex;
import woflo.petsplus.state.StateManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enchantment-Bound: owner-centric echoes and Arcane Focus surge.
 * Pet-attack agnostic, driven by owner events.
 */
public final class EnchantmentBoundHandler implements PlayerTickListener {
    private EnchantmentBoundHandler() {}

    private static final long SWIM_INTERVAL_TICKS = 10L;
    private static final Map<UUID, Long> NEXT_SWIM_TICK = new ConcurrentHashMap<>();

    private static final long IDLE_RECHECK_TICKS = 40L;

    private static final EnchantmentBoundHandler INSTANCE = new EnchantmentBoundHandler();

    public static EnchantmentBoundHandler getInstance() {
        return INSTANCE;
    }

    public static void initialize() {
        // Mining streaks -> brief Haste pulse
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (!(world instanceof ServerWorld serverWorld)) return;
            if (!(player instanceof ServerPlayerEntity)) return;
            if (!hasRoleNearby(player, PetRoleType.ENCHANTMENT_BOUND.id(), 16)) return;

            // Apply Haste pulse (owner-only)
            int base = PetsPlusConfig.getInstance().getRoleInt(PetRoleType.ENCHANTMENT_BOUND.id(), "miningHasteBaseTicks", 40);
            EnchantmentBoundEchoes.applyEnhancedHaste(player, base);

            // Arcane Focus: if focus bucket is mining and available, surge doubles effect durations
            maybeApplyArcaneFocus(player, FocusBucket.MINING);

            // Durability echo: small chance to refund 1 durability (approximate prevention)
            ItemStack tool = player.getMainHandStack();
            if (preventDurabilityLoss(player, tool)) {
                if (tool.isDamageable()) {
                    tool.setDamage(Math.max(0, tool.getDamage() - 1));
                }
            }

            // Extra roll for blocks (ores/crops/etc.) using loot logic
            int fortune = getEnchantLevel(player, Enchantments.FORTUNE);
            double baseChanceB = PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.ENCHANTMENT_BOUND.id(), "extraDuplicationChanceBase", 0.05);
            double chanceB = baseChanceB + fortune * 0.02;
            if (serverWorld.getRandom().nextDouble() < chanceB) {
                java.util.List<net.minecraft.item.ItemStack> drops = Block.getDroppedStacks(state, serverWorld, pos, world.getBlockEntity(pos), player, tool);
                for (var drop : drops) {
                    Block.dropStack(serverWorld, pos, drop.copy());
                }
            }
        });

        // On mob death by player -> small duplicate drop roll (Looting echo)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity.getWorld() instanceof ServerWorld serverWorld)) return;
            if (!(source.getAttacker() instanceof PlayerEntity owner)) return;
            if (!hasRoleNearby(owner, PetRoleType.ENCHANTMENT_BOUND.id(), 16)) return;

            // Basic whitelist guard: only for hostile mobs (no players/tamed)
            if (!(entity instanceof net.minecraft.entity.mob.Monster)) return;

            maybeApplyArcaneFocus(owner, FocusBucket.COMBAT);
            
            // Visual/audio feedback for proc
            serverWorld.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.ENTITY_ALLAY_ITEM_GIVEN, SoundCategory.PLAYERS, 0.2f, 1.2f);
        });

        // Proper mob loot table integration for extra drops
        LootTableEvents.MODIFY_DROPS.register((entry, context, drops) -> {
            // Must have player attacker parameter (implicitly checks for entity loot context)
            if (!context.hasParameter(LootContextParameters.ATTACKING_ENTITY)) return;
            
            var attacker = context.get(LootContextParameters.ATTACKING_ENTITY);
            if (!(attacker instanceof PlayerEntity owner)) return;
            
            // Must have Enchantment-Bound pet nearby
            if (!hasRoleNearby(owner, PetRoleType.ENCHANTMENT_BOUND.id(), 16)) return;
            
            // Must have killed entity parameter
            if (!context.hasParameter(LootContextParameters.THIS_ENTITY)) return;
            var killed = context.get(LootContextParameters.THIS_ENTITY);
            if (!(killed instanceof net.minecraft.entity.mob.Monster)) return;
            
            // Calculate extra drop chance based on Looting level
            int looting = getEnchantLevel(owner, Enchantments.LOOTING);
            double baseChance = PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.ENCHANTMENT_BOUND.id(), "extraDuplicationChanceBase", 0.05);
            double chance = baseChance + looting * 0.02;
            
            // Focus doubles the chance
            if (isFocusActive(owner, FocusBucket.COMBAT)) {
                chance *= 2.0;
            }
            
            if (context.getRandom().nextDouble() < chance) {
                // Duplicate a random existing drop from the table
                if (!drops.isEmpty()) {
                    ItemStack toDuplicate = drops.get(context.getRandom().nextInt(drops.size()));
                    drops.add(toDuplicate.copy());
                }
            }
        });

    }

    @Override
    public long nextRunTick(ServerPlayerEntity player) {
        if (player == null) {
            return Long.MAX_VALUE;
        }
        return NEXT_SWIM_TICK.getOrDefault(player.getUuid(), 0L);
    }

    @Override
    public void run(ServerPlayerEntity player, long currentTick) {
        if (player == null || player.isRemoved() || player.isSpectator()) {
            if (player != null) {
                NEXT_SWIM_TICK.remove(player.getUuid());
            }
            return;
        }

        UUID playerId = player.getUuid();
        NEXT_SWIM_TICK.put(playerId, currentTick + IDLE_RECHECK_TICKS);

        onOwnerSwimTick(player);
        NEXT_SWIM_TICK.put(playerId, currentTick + SWIM_INTERVAL_TICKS);
    }

    @Override
    public void onPlayerRemoved(ServerPlayerEntity player) {
        if (player != null) {
            NEXT_SWIM_TICK.remove(player.getUuid());
        }
    }

    /**
     * Called from a swim tick hook (optional): apply brief Dolphin's Grace when swimming with Aqua/Depth echoes.
     */
    public static void onOwnerSwimTick(PlayerEntity owner) {
        try {
            if (!(owner.getWorld() instanceof ServerWorld)) return;
            if (!hasRoleNearby(owner, PetRoleType.ENCHANTMENT_BOUND.id(), 16)) return;
            if (!owner.isTouchingWater()) return;

            int aqua = getEnchantLevel(owner, Enchantments.AQUA_AFFINITY);
            int depth = getEnchantLevel(owner, Enchantments.DEPTH_STRIDER);
            if (aqua > 0 || depth > 0) {
                owner.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 40, 0, false, false, true));
                maybeApplyArcaneFocus(owner, FocusBucket.SWIM);
            }
        } catch (Exception e) {
            // Log error but don't crash server
            System.err.println("EnchantmentBound swim tick error: " + e.getMessage());
        }
    }

    /**
     * Durability (Unbreaking echo): tiny chance to prevent owner tool damage on block break.
     */
    public static boolean preventDurabilityLoss(PlayerEntity owner, ItemStack tool) {
        try {
            if (!(owner.getWorld() instanceof ServerWorld serverWorld)) return false;
            if (!hasRoleNearby(owner, PetRoleType.ENCHANTMENT_BOUND.id(), 16)) return false;
            if (tool == null || tool.isEmpty() || !tool.isDamageable()) return false;

            double chance = PetsPlusConfig.getInstance().getRoleDouble(PetRoleType.ENCHANTMENT_BOUND.id(), "durabilityNoLossChance", 0.025);
            // Arcane Focus doubles the chance briefly when mining bucket is surging
            if (isFocusActive(owner, FocusBucket.MINING)) chance *= 2.0;
            return serverWorld.getRandom().nextDouble() < chance;
        } catch (Exception e) {
            System.err.println("EnchantmentBound durability prevention error: " + e.getMessage());
            return false;
        }
    }

    // ---- Arcane Focus (L20/L30) ---------------------------------------------------

    private enum FocusBucket { COMBAT, MINING, SWIM }

    private static void maybeApplyArcaneFocus(PlayerEntity owner, FocusBucket bucket) {
        try {
            if (!(owner instanceof ServerPlayerEntity)) return;
            int level = nearestRoleLevel(owner, PetRoleType.ENCHANTMENT_BOUND.id(), 16);
            if (level < 20) return; // focus unlock at ~L20

            if (isFocusActive(owner, bucket)) return; // already active
            if (!consumeFocusCharge(owner)) return;   // no charges available

            int duration = PetsPlusConfig.getInstance().getRoleInt(PetRoleType.ENCHANTMENT_BOUND.id(), "focusSurgeDurationTicks", 200);
            long now = owner.getWorld().getTime();
            storeFocusState(owner, bucket, now + duration);

            // Subtle feedback
            owner.getWorld().playSound(null, owner.getX(), owner.getY(), owner.getZ(),
                SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.5f, 1.3f);
        } catch (Exception e) {
            System.err.println("EnchantmentBound Arcane Focus error: " + e.getMessage());
        }
    }

    private static boolean isFocusActive(PlayerEntity owner, FocusBucket bucket) {
        try {
            Long until = getOwnerTempLong(owner, "eb_focus_" + bucket.name().toLowerCase());
            return until != null && owner.getWorld().getTime() < until;
        } catch (Exception e) {
            System.err.println("EnchantmentBound focus state check error: " + e.getMessage());
            return false;
        }
    }

    private static void storeFocusState(PlayerEntity owner, FocusBucket bucket, long until) {
        try {
            setOwnerTempLong(owner, "eb_focus_" + bucket.name().toLowerCase(), until);
        } catch (Exception e) {
            System.err.println("EnchantmentBound focus state storage error: " + e.getMessage());
        }
    }

    private static boolean consumeFocusCharge(PlayerEntity owner) {
        try {
            int level = nearestRoleLevel(owner, PetRoleType.ENCHANTMENT_BOUND.id(), 16);
            int maxCharges = level >= 30 ? 2 : 1;
            int cd = PetsPlusConfig.getInstance().getRoleInt(PetRoleType.ENCHANTMENT_BOUND.id(), "focusCooldownTicks", 1200); // 60s
            long now = owner.getWorld().getTime();
            Long last = getOwnerTempLong(owner, "eb_focus_last");
            Integer used = getOwnerTempInt(owner, "eb_focus_used");
            if (last != null) {
                long elapsed = now - last;
                if (elapsed >= cd) {
                    // Cooldown passed: replenish charges
                    setOwnerTempInt(owner, "eb_focus_used", 0);
                    used = 0;
                } else if (used != null && used >= maxCharges) {
                    return false; // Still on cooldown and out of charges
                }
            }
            int cur = used == null ? 0 : used;
            if (cur >= maxCharges) return false;
            setOwnerTempInt(owner, "eb_focus_used", cur + 1);
            setOwnerTempLong(owner, "eb_focus_last", now);
            return true;
        } catch (Exception e) {
            System.err.println("EnchantmentBound focus charge consumption error: " + e.getMessage());
            return false;
        }
    }

    // ---- Helpers ------------------------------------------------------------------

    private static boolean hasRoleNearby(PlayerEntity owner, Identifier roleId, double radius) {
        try {
            if (!(owner instanceof ServerPlayerEntity serverOwner)) return false;
            if (!(serverOwner.getWorld() instanceof ServerWorld serverWorld)) return false;

            StateManager stateManager = StateManager.forWorld(serverWorld);
            PetSwarmIndex swarmIndex = stateManager.getSwarmIndex();
            if (swarmIndex == null) {
                return false;
            }

            Vec3d center = serverOwner.getPos();
            boolean[] found = new boolean[1];
            swarmIndex.forEachPetInRange(serverOwner, center, radius, entry -> {
                if (found[0]) {
                    return;
                }
                PetComponent component = entry.component();
                if (component == null) {
                    return;
                }
                if (!entry.pet().isAlive()) {
                    return;
                }
                if (!component.hasRole(roleId)) {
                    return;
                }
                if (!component.isOwnedBy(owner)) {
                    return;
                }
                found[0] = true;
            });
            return found[0];
        } catch (Exception e) {
            System.err.println("EnchantmentBound role check error: " + e.getMessage());
            return false;
        }
    }

    private static int nearestRoleLevel(PlayerEntity owner, Identifier roleId, double radius) {
        try {
            if (!(owner instanceof ServerPlayerEntity serverOwner)) return 0;
            if (!(serverOwner.getWorld() instanceof ServerWorld serverWorld)) return 0;

            StateManager stateManager = StateManager.forWorld(serverWorld);
            PetSwarmIndex swarmIndex = stateManager.getSwarmIndex();
            if (swarmIndex == null) {
                return 0;
            }

            Vec3d center = serverOwner.getPos();
            int[] best = new int[1];
            swarmIndex.forEachPetInRange(serverOwner, center, radius, entry -> {
                PetComponent component = entry.component();
                if (component == null || !entry.pet().isAlive()) {
                    return;
                }
                if (!component.hasRole(roleId)) {
                    return;
                }
                if (!component.isOwnedBy(owner)) {
                    return;
                }
                best[0] = Math.max(best[0], component.getLevel());
            });
            return best[0];
        } catch (Exception e) {
            System.err.println("EnchantmentBound role level check error: " + e.getMessage());
            return 0;
        }
    }

    private static int getEnchantLevel(PlayerEntity owner, RegistryKey<Enchantment> enchKey) {
        try {
            RegistryEntry<Enchantment> entry = owner.getWorld()
                    .getRegistryManager()
                    .getOrThrow(RegistryKeys.ENCHANTMENT)
                    .getOrThrow(enchKey);
            return EnchantmentHelper.getEquipmentLevel(entry, owner);
        } catch (Exception e) {
            // If enchantment lookup fails, assume level 0
            return 0;
        }
    }

    // Use OwnerCombatState temp storage to avoid new components
    private static Long getOwnerTempLong(PlayerEntity owner, String key) {
        try {
            var s = woflo.petsplus.state.OwnerCombatState.getOrCreate(owner);
            long v = s.getTempState(key);
            return v == 0L ? null : v;
        } catch (Exception e) {
            System.err.println("EnchantmentBound temp state get error: " + e.getMessage());
            return null;
        }
    }
    private static void setOwnerTempLong(PlayerEntity owner, String key, long value) {
        try {
            woflo.petsplus.state.OwnerCombatState.getOrCreate(owner).setTempState(key, value);
        } catch (Exception e) {
            System.err.println("EnchantmentBound temp state set error: " + e.getMessage());
        }
    }
    private static Integer getOwnerTempInt(PlayerEntity owner, String key) {
        try {
            var s = woflo.petsplus.state.OwnerCombatState.getOrCreate(owner);
            long v = s.getTempState(key);
            return v == 0L ? null : (int) v;
        } catch (Exception e) {
            System.err.println("EnchantmentBound temp state get error: " + e.getMessage());
            return null;
        }
    }
    private static void setOwnerTempInt(PlayerEntity owner, String key, int value) {
        try {
            woflo.petsplus.state.OwnerCombatState.getOrCreate(owner).setTempState(key, value);
        } catch (Exception e) {
            System.err.println("EnchantmentBound temp state set error: " + e.getMessage());
        }
    }
}
