package woflo.petsplus.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.Petsplus;
import woflo.petsplus.naming.AttributeKey;
import woflo.petsplus.naming.AttributeRegistry;
import woflo.petsplus.naming.NameParser;
import woflo.petsplus.naming.NamingAPI;
import woflo.petsplus.state.PetComponent;

import java.util.List;

/**
 * Mixin to intercept entity name changes and parse name-based attributes for pets.
 * This hooks into Entity.setCustomName to automatically detect and apply pet attributes
 * based on the name patterns configured in the naming system.
 */
@Mixin(Entity.class)
public class PetNamingMixin {

    /**
     * Inject into setCustomName to parse and apply name-based attributes.
     * This runs after the name is set, ensuring the custom name is available
     * when we parse it for attributes.
     */
    @Inject(method = "setCustomName", at = @At("TAIL"))
    private void onSetCustomName(@Nullable Text name, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        // Early filter: only process MobEntities
        if (!(entity instanceof MobEntity mob)) {
            return;
        }

        // Early filter: only process entities that have a PetComponent
        PetComponent component = PetComponent.get(mob);
        if (component == null) {
            return;
        }

        try {
            // Parse name attributes if name exists
            if (name != null) {
                String nameString = name.getString();
                List<AttributeKey> attributes = NameParser.parseWithCache(mob.getUuid(), nameString);

                // Update component with new attributes
                component.setNameAttributes(attributes);

                // Fire the naming event for external mod integration
                ActionResult eventResult = NamingAPI.firePetNamedEvent(mob, attributes, component);

                // Apply attributes through registry system unless cancelled by event
                if (eventResult != ActionResult.FAIL) {
                    AttributeRegistry.applyAll(mob, attributes, component);
                }

                if (!attributes.isEmpty()) {
                    Petsplus.LOGGER.debug("Parsed and applied {} attributes from name '{}' for pet {}",
                        attributes.size(), nameString, mob.getUuid());
                }
            } else {
                // Clear attributes if name is set to null
                component.setNameAttributes(List.of());
                NameParser.clearCache(mob.getUuid());

                Petsplus.LOGGER.debug("Cleared name attributes for pet {} (name set to null)", mob.getUuid());
            }
        } catch (Exception e) {
            Petsplus.LOGGER.error("Error processing name attributes for pet {}: {}",
                mob.getUuid(), e.getMessage(), e);
        }
    }
}