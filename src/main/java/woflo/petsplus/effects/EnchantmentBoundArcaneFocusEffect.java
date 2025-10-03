package woflo.petsplus.effects;

import com.google.gson.JsonObject;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.RegistryJsonHelper;
import woflo.petsplus.config.PetsPlusConfig;
import woflo.petsplus.roles.enchantmentbound.EnchantmentBoundFocusHelper;
import woflo.petsplus.state.PetComponent;

/**
 * Handles the Arcane Focus surge activation for Enchantment-Bound owners.
 */
public class EnchantmentBoundArcaneFocusEffect implements Effect {
    private static final Identifier ID = Identifier.of(Petsplus.MOD_ID, "enchantment_arcane_focus");

    private final EnchantmentBoundFocusHelper.Bucket bucket;
    private final int durationTicks;
    private final int cooldownTicks;
    private final int chargesAtThirty;
    private final int minimumLevel;
    private final boolean playSound;
    private final boolean requireHostileVictim;

    public EnchantmentBoundArcaneFocusEffect(JsonObject json) {
        String bucketName = RegistryJsonHelper.getString(json, "bucket", "mining");
        this.bucket = switch (bucketName.toLowerCase()) {
            case "combat" -> EnchantmentBoundFocusHelper.Bucket.COMBAT;
            case "swim" -> EnchantmentBoundFocusHelper.Bucket.SWIM;
            default -> EnchantmentBoundFocusHelper.Bucket.MINING;
        };
        PetsPlusConfig config = PetsPlusConfig.getInstance();
        Identifier roleId = PetRoleType.ENCHANTMENT_BOUND.id();
        this.durationTicks = Math.max(1, RegistryJsonHelper.getInt(json, "duration_ticks",
            config.getRoleInt(roleId, "focusSurgeDurationTicks", 200)));
        this.cooldownTicks = Math.max(1, RegistryJsonHelper.getInt(json, "cooldown_ticks",
            config.getRoleInt(roleId, "focusCooldownTicks", 1200)));
        this.chargesAtThirty = Math.max(1, RegistryJsonHelper.getInt(json, "charges_at_level_30", 2));
        this.minimumLevel = Math.max(1, RegistryJsonHelper.getInt(json, "min_level", 20));
        this.playSound = RegistryJsonHelper.getBoolean(json, "play_sound", true);
        this.requireHostileVictim = RegistryJsonHelper.getBoolean(json, "require_hostile_victim", true);
    }

    public EnchantmentBoundArcaneFocusEffect() {
        this.bucket = EnchantmentBoundFocusHelper.Bucket.MINING;
        this.durationTicks = 200;
        this.cooldownTicks = 1200;
        this.chargesAtThirty = 2;
        this.minimumLevel = 20;
        this.playSound = true;
        this.requireHostileVictim = true;
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getOwner() instanceof ServerPlayerEntity owner)) {
            return false;
        }
        if (!(context.getWorld() instanceof ServerWorld)) {
            return false;
        }
        MobEntity pet = context.getPet();
        PetComponent component = PetComponent.get(pet);
        if (component == null || !component.hasRole(PetRoleType.ENCHANTMENT_BOUND) || component.getLevel() < minimumLevel) {
            return false;
        }

        if (bucket == EnchantmentBoundFocusHelper.Bucket.COMBAT && requireHostileVictim) {
            Boolean hostile = context.getTriggerContext().getData("victim_was_hostile", Boolean.class);
            if (hostile != null && !hostile) {
                return false;
            }
            if (hostile == null) {
                if (!(context.getTriggerContext().getVictim() instanceof HostileEntity)) {
                    return false;
                }
            }
        }

        return EnchantmentBoundFocusHelper.tryActivate(owner, bucket, component.getLevel(),
            durationTicks, cooldownTicks, chargesAtThirty, playSound);
    }
}
