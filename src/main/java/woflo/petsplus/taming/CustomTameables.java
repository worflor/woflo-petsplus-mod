package woflo.petsplus.taming;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Registry of server-side tameable definitions for entities that don't extend
 * vanilla {@link net.minecraft.entity.passive.TameableEntity}. The registry is
 * intentionally lightweight so other animals can reuse the same bridge.
 */
public final class CustomTameables {

    private static final Map<EntityType<? extends MobEntity>, Definition> DEFINITIONS = new ConcurrentHashMap<>();
    private static volatile boolean defaultsRegistered;

    private CustomTameables() {}

    public static void ensureDefaultsRegistered() {
        if (defaultsRegistered) {
            return;
        }
        register(EntityType.RABBIT, Definition.forItems(
            SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
            Items.CARROT, Items.GOLDEN_CARROT, Items.DANDELION
        ));
        register(EntityType.FROG, Definition.forItems(
            SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,
            SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
            Items.SLIME_BALL
        ));
        defaultsRegistered = true;
    }

    public static void register(EntityType<? extends MobEntity> type, Definition definition) {
        DEFINITIONS.put(type, definition);
    }

    @Nullable
    public static Definition get(EntityType<?> type) {
        @SuppressWarnings("unchecked")
        EntityType<? extends MobEntity> mobType = (EntityType<? extends MobEntity>) type;
        return DEFINITIONS.get(mobType);
    }

    public static Collection<Map.Entry<EntityType<? extends MobEntity>, Definition>> entries() {
        return Collections.unmodifiableCollection(DEFINITIONS.entrySet());
    }

    public record Definition(Predicate<ItemStack> tamingPredicate,
                             boolean consumeItem,
                             SoundEvent tameSound,
                             SoundEvent sitSound) {

        public boolean isTamingItem(ItemStack stack) {
            return this.tamingPredicate.test(stack);
        }

        public static Definition forItems(SoundEvent tameSound, SoundEvent sitSound, Item... items) {
            Set<Item> allowed = Set.of(items);
            Predicate<ItemStack> predicate = stack -> !stack.isEmpty() && allowed.contains(stack.getItem());
            return new Definition(predicate, true, tameSound, sitSound);
        }

        public static Builder builder(Predicate<ItemStack> predicate) {
            return new Builder(predicate);
        }

        public static final class Builder {
            private final Predicate<ItemStack> predicate;
            private boolean consumeItem = true;
            private SoundEvent tameSound = SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE;
            private SoundEvent sitSound = SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;

            private Builder(Predicate<ItemStack> predicate) {
                this.predicate = predicate;
            }

            public Builder consumeItem(boolean consume) {
                this.consumeItem = consume;
                return this;
            }

            public Builder tameSound(SoundEvent sound) {
                this.tameSound = sound;
                return this;
            }

            public Builder sitSound(SoundEvent sound) {
                this.sitSound = sound;
                return this;
            }

            public Definition build() {
                return new Definition(this.predicate, this.consumeItem, this.tameSound, this.sitSound);
            }
        }
    }
}

