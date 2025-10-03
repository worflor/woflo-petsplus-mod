package woflo.petsplus.roles.enchantmentbound;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import woflo.petsplus.state.PetComponent;
import woflo.petsplus.state.StateManager;

/**
 * Bridges Fabric loot table events into the ability system so Enchantment-Bound
 * effects can data-drive loot duplication.
 */
public final class EnchantmentBoundAbilityHooks {

    private EnchantmentBoundAbilityHooks() {
    }

    public static void initialize() {
        LootTableEvents.MODIFY_DROPS.register((resource, context, drops) -> {
            if (!context.hasParameter(LootContextParameters.ATTACKING_ENTITY)) {
                return;
            }
            Entity attacker = context.get(LootContextParameters.ATTACKING_ENTITY);
            ServerPlayerEntity owner = resolveOwner(attacker);
            if (owner == null) {
                return;
            }
            ServerWorld world = (ServerWorld) owner.getWorld();
            if (world == null) {
                return;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("drops", drops);
            payload.put("loot_random", context.getRandom());

            if (context.hasParameter(LootContextParameters.THIS_ENTITY)) {
                Entity victim = context.get(LootContextParameters.THIS_ENTITY);
                payload.put("victim", victim);
                payload.put("victim_was_hostile", victim instanceof HostileEntity);
            }

            StateManager.forWorld(world).dispatchAbilityTrigger(owner, "loot_table_modify", payload);
        });
    }

    private static ServerPlayerEntity resolveOwner(Entity attacker) {
        if (attacker instanceof ServerPlayerEntity serverOwner) {
            return serverOwner;
        }
        if (attacker instanceof PlayerEntity player && player.getWorld() instanceof ServerWorld) {
            return (ServerPlayerEntity) player;
        }
        if (attacker instanceof net.minecraft.entity.mob.MobEntity mob) {
            PetComponent component = PetComponent.get(mob);
            if (component != null) {
                PlayerEntity owner = component.getOwner();
                if (owner instanceof ServerPlayerEntity serverOwner) {
                    return serverOwner;
                }
            }
        }
        return null;
    }
}
