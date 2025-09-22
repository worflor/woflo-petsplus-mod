package woflo.petsplus.events;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.PetRole;
import woflo.petsplus.state.PetComponent;

/**
 * Handles right-click interactions to give potions to Support pets.
 */
public class SupportInteractionHandler {

    public static void register() {
        UseEntityCallback.EVENT.register(SupportInteractionHandler::onUseEntity);
        Petsplus.LOGGER.info("Support interaction handler registered");
    }

    private static ActionResult onUseEntity(PlayerEntity player, net.minecraft.world.World world, Hand hand, Entity entity, EntityHitResult hitResult) {
        if (world.isClient()) return ActionResult.PASS;
        if (!(entity instanceof MobEntity mob)) return ActionResult.PASS;

        PetComponent comp = PetComponent.get(mob);
        if (comp == null || comp.getRole() != PetRole.SUPPORT) return ActionResult.PASS;

        // Must be owner
        if (!comp.isOwnedBy(player)) return ActionResult.PASS;

        // Must be holding a potion (regular, splash, or lingering)
        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.isOf(Items.POTION) || stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION))) {
            return ActionResult.PASS;
        }

        // Enforce single-slot: if already has a stored potion, reject
        if (Boolean.TRUE.equals(comp.getStateData("support_potion_present", Boolean.class))) {
            player.sendMessage(net.minecraft.text.Text.literal("Your companion is already holding a potion"), true);
            return ActionResult.SUCCESS; // handled
        }

        // Extract effects and store lightweight representation using pet level for duration calculation
        var effects = woflo.petsplus.roles.support.SupportPotionUtils.getAuraEffects(stack, comp.getLevel());
        if (effects.isEmpty()) {
            return ActionResult.PASS; // Not a potion with effects (e.g., water)
        }

        // Calculate appropriate aura pulse duration based on original potion and pet level
        int basePotionDuration = effects.isEmpty() ? 3600 : effects.get(0).getDuration(); // Use first effect or default
        int auraPulseDuration = woflo.petsplus.roles.support.SupportPotionUtils.getAuraPulseDuration(basePotionDuration, comp.getLevel());

        // Serialize to simple strings: "namespace:path|amp"
        java.util.List<String> serialized = new java.util.ArrayList<>();
        for (var e : effects) {
            var id = net.minecraft.registry.Registries.STATUS_EFFECT.getId(e.getEffectType().value());
            if (id != null) {
                serialized.add(id.toString() + "|" + Math.max(0, e.getAmplifier()));
            }
        }
        comp.setStateData("support_potion_effects", serialized);
        comp.setStateData("support_potion_present", true);
        comp.setStateData("support_potion_aura_duration", auraPulseDuration);

        // Consume exactly one from the player stack
        stack.decrement(1);

        // Feedback
        ((ServerWorld) world).spawnParticles(net.minecraft.particle.ParticleTypes.HEART,
            mob.getX(), mob.getY() + mob.getHeight() * 0.6, mob.getZ(),
            7, 0.2, 0.3, 0.2, 0.02);
        player.sendMessage(net.minecraft.text.Text.literal("Your support companion holds the potion for allies"), true);

        return ActionResult.SUCCESS;
    }
}
