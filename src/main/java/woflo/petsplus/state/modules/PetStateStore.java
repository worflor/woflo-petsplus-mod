package woflo.petsplus.state.modules;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/**
 * Dense, typed storage for per-pet dynamic state. Known keys are mapped onto
 * primitive arrays so hot-path lookups avoid boxing while still providing a
 * Map-like API for legacy callers.
 */
public final class PetStateStore {

    private enum SlotType { LONG, INT, FLOAT, BOOLEAN, OBJECT }

    private record Slot(SlotType type, int index, String key) {}

    private static final Map<String, Slot> LOOKUP = new Object2ObjectOpenHashMap<>();
    private static final String[] LONG_KEYS = new String[LongSlot.values().length];
    private static final String[] INT_KEYS = new String[IntSlot.values().length];
    private static final String[] FLOAT_KEYS = new String[FloatSlot.values().length];
    private static final String[] BOOLEAN_KEYS = new String[BooleanSlot.values().length];
    private static final String[] OBJECT_KEYS = new String[ObjectSlot.values().length];

    private enum LongSlot {
        TAMED_TICK,
        LAST_PET_TIME,
        LAST_SOCIAL_BUFFER_TICK,
        LAST_CROUCH_CUDDLE_TICK,
        SNUGGLE_LAST_START_TICK,
        SNUGGLE_COOLDOWN_UNTIL_TICK,
        GOSSIP_OPT_OUT_UNTIL,
        GOSSIP_LAST_WANDER_TICK,
        THREAT_LAST_TICK,
        THREAT_LAST_DANGER,
        THREAT_LAST_RECOVERY_TICK,
        OWNER_LAST_HURT_TICK,
        OWNER_LAST_LOW_HEALTH_TICK,
        OWNER_LAST_STATUS_HAZARD_TICK,
        OWNER_LAST_NEARBY_TICK,
        OWNER_LAST_SEEN_TICK,
        SURVEY_LAST_TARGET_TICK,
        SURVEY_LAST_SEARCH_TICK,
        HEALTH_LAST_LOW_TICK,
        HEALTH_RECOVERY_COOLDOWN,
        PACK_LAST_NEARBY_TICK,
        ARCANE_LAST_ENCHANT_TICK,
        ARCANE_LAST_SURGE_TICK,
        ARCANE_LAST_SCAN_TICK,
        LAST_PLAY_INTERACTION_TICK,
        LAST_FEED_TICK,
        LAST_GIFT_TICK,
        BREEDING_BIRTH_TICK,
        BREEDING_BIRTH_TIME_OF_DAY
    }

    private enum IntSlot {
        PET_COUNT,
        SOCIAL_JITTER_SEED,
        GOSSIP_CLUSTER_CURSOR,
        GOSSIP_STALL_COUNT,
        THREAT_SAFE_STREAK,
        THREAT_SENSITIZED_STREAK,
        PACK_LAST_NEARBY_ALLIES,
        ARCANE_ENCHANT_STREAK,
        BREEDING_BIRTH_NEARBY_PLAYER_COUNT,
        BREEDING_BIRTH_NEARBY_PET_COUNT
    }

    private enum FloatSlot {
        OWNER_LAST_HURT_SEVERITY,
        OWNER_LAST_HEALTH_RATIO,
        OWNER_LAST_STATUS_HAZARD_SEVERITY,
        OWNER_LAST_NEARBY_DISTANCE,
        OWNER_LAST_SEEN_DISTANCE,
        PACK_LAST_NEARBY_STRENGTH,
        PACK_LAST_NEARBY_WEIGHTED_STRENGTH,
        PACK_LAST_ROLE_DIVERSITY,
        ARCANE_SURGE_STRENGTH,
        ARCANE_CACHED_AMBIENT_ENERGY
    }

    private enum BooleanSlot {
        BREEDING_INHERITED_STATS,
        BREEDING_BIRTH_IS_DAYTIME,
        BREEDING_BIRTH_IS_INDOORS,
        BREEDING_BIRTH_IS_RAINING,
        BREEDING_BIRTH_IS_THUNDERING
    }

    private enum ObjectSlot {
        LAST_SAFE_WATER_POS,
        SURVEY_LAST_TARGET_POS,
        SURVEY_LAST_TARGET_ID,
        OWNER_LAST_SEEN_DIMENSION,
        SURVEY_LAST_TARGET_KIND,
        SURVEY_LAST_TARGET_DIMENSION,
        ARCANE_LAST_SCAN_POS,
        BREEDING_PARENT_A_UUID,
        BREEDING_PARENT_B_UUID,
        BREEDING_OWNER_UUID,
        BREEDING_PRIMARY_ROLE,
        BREEDING_PARTNER_ROLE,
        BREEDING_INHERITED_ROLE,
        BREEDING_SOURCE,
        BREEDING_BIRTH_DIMENSION,
        BREEDING_ASSIGNED_NATURE,
        ASSIGNED_NATURE,
        WILD_ASSIGNED_NATURE,
        ASTROLOGY_SIGN
    }

    static {
        registerLong(LongSlot.TAMED_TICK, PetComponent.StateKeys.TAMED_TICK);
        registerLong(LongSlot.LAST_PET_TIME, PetComponent.StateKeys.LAST_PET_TIME);
        registerInt(IntSlot.PET_COUNT, PetComponent.StateKeys.PET_COUNT);
        registerLong(LongSlot.LAST_SOCIAL_BUFFER_TICK, PetComponent.StateKeys.LAST_SOCIAL_BUFFER_TICK);
        registerLong(LongSlot.LAST_CROUCH_CUDDLE_TICK, PetComponent.StateKeys.LAST_CROUCH_CUDDLE_TICK);
        registerLong(LongSlot.SNUGGLE_LAST_START_TICK, PetComponent.StateKeys.SNUGGLE_LAST_START_TICK);
        registerLong(LongSlot.SNUGGLE_COOLDOWN_UNTIL_TICK, PetComponent.StateKeys.SNUGGLE_COOLDOWN_UNTIL_TICK);
        registerInt(IntSlot.SOCIAL_JITTER_SEED, PetComponent.StateKeys.SOCIAL_JITTER_SEED);
        registerLong(LongSlot.GOSSIP_OPT_OUT_UNTIL, PetComponent.StateKeys.GOSSIP_OPT_OUT_UNTIL);
        registerInt(IntSlot.GOSSIP_CLUSTER_CURSOR, PetComponent.StateKeys.GOSSIP_CLUSTER_CURSOR);
        registerInt(IntSlot.GOSSIP_STALL_COUNT, PetComponent.StateKeys.GOSSIP_STALL_COUNT);
        registerLong(LongSlot.THREAT_LAST_TICK, PetComponent.StateKeys.THREAT_LAST_TICK);
        registerInt(IntSlot.THREAT_SAFE_STREAK, PetComponent.StateKeys.THREAT_SAFE_STREAK);
        registerInt(IntSlot.THREAT_SENSITIZED_STREAK, PetComponent.StateKeys.THREAT_SENSITIZED_STREAK);
        registerLong(LongSlot.THREAT_LAST_DANGER, PetComponent.StateKeys.THREAT_LAST_DANGER);
        registerLong(LongSlot.THREAT_LAST_RECOVERY_TICK, PetComponent.StateKeys.THREAT_LAST_RECOVERY_TICK);
        registerObject(ObjectSlot.LAST_SAFE_WATER_POS, PetComponent.StateKeys.LAST_SAFE_WATER_POS);
        registerLong(LongSlot.OWNER_LAST_HURT_TICK, PetComponent.StateKeys.OWNER_LAST_HURT_TICK);
        registerFloat(FloatSlot.OWNER_LAST_HURT_SEVERITY, PetComponent.StateKeys.OWNER_LAST_HURT_SEVERITY);
        registerFloat(FloatSlot.OWNER_LAST_HEALTH_RATIO, PetComponent.StateKeys.OWNER_LAST_HEALTH_RATIO);
        registerLong(LongSlot.OWNER_LAST_LOW_HEALTH_TICK, PetComponent.StateKeys.OWNER_LAST_LOW_HEALTH_TICK);
        registerLong(LongSlot.OWNER_LAST_STATUS_HAZARD_TICK, PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_TICK);
        registerFloat(FloatSlot.OWNER_LAST_STATUS_HAZARD_SEVERITY, PetComponent.StateKeys.OWNER_LAST_STATUS_HAZARD_SEVERITY);
        registerLong(LongSlot.OWNER_LAST_NEARBY_TICK, PetComponent.StateKeys.OWNER_LAST_NEARBY_TICK);
        registerFloat(FloatSlot.OWNER_LAST_NEARBY_DISTANCE, PetComponent.StateKeys.OWNER_LAST_NEARBY_DISTANCE);
        registerLong(LongSlot.OWNER_LAST_SEEN_TICK, PetComponent.StateKeys.OWNER_LAST_SEEN_TICK);
        registerFloat(FloatSlot.OWNER_LAST_SEEN_DISTANCE, PetComponent.StateKeys.OWNER_LAST_SEEN_DISTANCE);
        registerObject(ObjectSlot.OWNER_LAST_SEEN_DIMENSION, PetComponent.StateKeys.OWNER_LAST_SEEN_DIMENSION);
        registerLong(LongSlot.GOSSIP_LAST_WANDER_TICK, PetComponent.StateKeys.GOSSIP_LAST_WANDER_TICK);
        registerObject(ObjectSlot.SURVEY_LAST_TARGET_POS, PetComponent.StateKeys.SURVEY_LAST_TARGET_POS);
        registerObject(ObjectSlot.SURVEY_LAST_TARGET_ID, PetComponent.StateKeys.SURVEY_LAST_TARGET_ID);
        registerObject(ObjectSlot.SURVEY_LAST_TARGET_KIND, PetComponent.StateKeys.SURVEY_LAST_TARGET_KIND);
        registerObject(ObjectSlot.SURVEY_LAST_TARGET_DIMENSION, PetComponent.StateKeys.SURVEY_LAST_TARGET_DIMENSION);
        registerLong(LongSlot.SURVEY_LAST_TARGET_TICK, PetComponent.StateKeys.SURVEY_LAST_TARGET_TICK);
        registerLong(LongSlot.SURVEY_LAST_SEARCH_TICK, PetComponent.StateKeys.SURVEY_LAST_SEARCH_TICK);
        registerLong(LongSlot.HEALTH_LAST_LOW_TICK, PetComponent.StateKeys.HEALTH_LAST_LOW_TICK);
        registerLong(LongSlot.HEALTH_RECOVERY_COOLDOWN, PetComponent.StateKeys.HEALTH_RECOVERY_COOLDOWN);
        registerLong(LongSlot.PACK_LAST_NEARBY_TICK, PetComponent.StateKeys.PACK_LAST_NEARBY_TICK);
        registerFloat(FloatSlot.PACK_LAST_NEARBY_STRENGTH, PetComponent.StateKeys.PACK_LAST_NEARBY_STRENGTH);
        registerFloat(FloatSlot.PACK_LAST_NEARBY_WEIGHTED_STRENGTH, PetComponent.StateKeys.PACK_LAST_NEARBY_WEIGHTED_STRENGTH);
        registerInt(IntSlot.PACK_LAST_NEARBY_ALLIES, PetComponent.StateKeys.PACK_LAST_NEARBY_ALLIES);
        registerFloat(FloatSlot.PACK_LAST_ROLE_DIVERSITY, PetComponent.StateKeys.PACK_LAST_ROLE_DIVERSITY);
        registerLong(LongSlot.ARCANE_LAST_ENCHANT_TICK, PetComponent.StateKeys.ARCANE_LAST_ENCHANT_TICK);
        registerInt(IntSlot.ARCANE_ENCHANT_STREAK, PetComponent.StateKeys.ARCANE_ENCHANT_STREAK);
        registerLong(LongSlot.ARCANE_LAST_SURGE_TICK, PetComponent.StateKeys.ARCANE_LAST_SURGE_TICK);
        registerFloat(FloatSlot.ARCANE_SURGE_STRENGTH, PetComponent.StateKeys.ARCANE_SURGE_STRENGTH);
        registerLong(LongSlot.ARCANE_LAST_SCAN_TICK, PetComponent.StateKeys.ARCANE_LAST_SCAN_TICK);
        registerFloat(FloatSlot.ARCANE_CACHED_AMBIENT_ENERGY, PetComponent.StateKeys.ARCANE_CACHED_AMBIENT_ENERGY);
        registerObject(ObjectSlot.ARCANE_LAST_SCAN_POS, PetComponent.StateKeys.ARCANE_LAST_SCAN_POS);
        registerLong(LongSlot.LAST_PLAY_INTERACTION_TICK, PetComponent.StateKeys.LAST_PLAY_INTERACTION_TICK);
        registerLong(LongSlot.LAST_FEED_TICK, PetComponent.StateKeys.LAST_FEED_TICK);
        registerLong(LongSlot.LAST_GIFT_TICK, PetComponent.StateKeys.LAST_GIFT_TICK);
        registerLong(LongSlot.BREEDING_BIRTH_TICK, PetComponent.StateKeys.BREEDING_BIRTH_TICK);
        registerObject(ObjectSlot.BREEDING_PARENT_A_UUID, PetComponent.StateKeys.BREEDING_PARENT_A_UUID);
        registerObject(ObjectSlot.BREEDING_PARENT_B_UUID, PetComponent.StateKeys.BREEDING_PARENT_B_UUID);
        registerObject(ObjectSlot.BREEDING_OWNER_UUID, PetComponent.StateKeys.BREEDING_OWNER_UUID);
        registerObject(ObjectSlot.BREEDING_PRIMARY_ROLE, PetComponent.StateKeys.BREEDING_PRIMARY_ROLE);
        registerObject(ObjectSlot.BREEDING_PARTNER_ROLE, PetComponent.StateKeys.BREEDING_PARTNER_ROLE);
        registerObject(ObjectSlot.BREEDING_INHERITED_ROLE, PetComponent.StateKeys.BREEDING_INHERITED_ROLE);
        registerBoolean(BooleanSlot.BREEDING_INHERITED_STATS, PetComponent.StateKeys.BREEDING_INHERITED_STATS);
        registerObject(ObjectSlot.BREEDING_SOURCE, PetComponent.StateKeys.BREEDING_SOURCE);
        registerLong(LongSlot.BREEDING_BIRTH_TIME_OF_DAY, PetComponent.StateKeys.BREEDING_BIRTH_TIME_OF_DAY);
        registerBoolean(BooleanSlot.BREEDING_BIRTH_IS_DAYTIME, PetComponent.StateKeys.BREEDING_BIRTH_IS_DAYTIME);
        registerBoolean(BooleanSlot.BREEDING_BIRTH_IS_INDOORS, PetComponent.StateKeys.BREEDING_BIRTH_IS_INDOORS);
        registerBoolean(BooleanSlot.BREEDING_BIRTH_IS_RAINING, PetComponent.StateKeys.BREEDING_BIRTH_IS_RAINING);
        registerBoolean(BooleanSlot.BREEDING_BIRTH_IS_THUNDERING, PetComponent.StateKeys.BREEDING_BIRTH_IS_THUNDERING);
        registerInt(IntSlot.BREEDING_BIRTH_NEARBY_PLAYER_COUNT, PetComponent.StateKeys.BREEDING_BIRTH_NEARBY_PLAYER_COUNT);
        registerInt(IntSlot.BREEDING_BIRTH_NEARBY_PET_COUNT, PetComponent.StateKeys.BREEDING_BIRTH_NEARBY_PET_COUNT);
        registerObject(ObjectSlot.BREEDING_BIRTH_DIMENSION, PetComponent.StateKeys.BREEDING_BIRTH_DIMENSION);
        registerObject(ObjectSlot.BREEDING_ASSIGNED_NATURE, PetComponent.StateKeys.BREEDING_ASSIGNED_NATURE);
        registerObject(ObjectSlot.ASSIGNED_NATURE, PetComponent.StateKeys.ASSIGNED_NATURE);
        registerObject(ObjectSlot.WILD_ASSIGNED_NATURE, PetComponent.StateKeys.WILD_ASSIGNED_NATURE);
        registerObject(ObjectSlot.ASTROLOGY_SIGN, PetComponent.StateKeys.ASTROLOGY_SIGN);
    }

    private final long[] longValues = new long[LongSlot.values().length];
    private final boolean[] longPresent = new boolean[LongSlot.values().length];

    private final int[] intValues = new int[IntSlot.values().length];
    private final boolean[] intPresent = new boolean[IntSlot.values().length];

    private final float[] floatValues = new float[FloatSlot.values().length];
    private final boolean[] floatPresent = new boolean[FloatSlot.values().length];

    private final boolean[] booleanValues = new boolean[BooleanSlot.values().length];
    private final boolean[] booleanPresent = new boolean[BooleanSlot.values().length];

    private final Object[] objectValues = new Object[ObjectSlot.values().length];
    private final boolean[] objectPresent = new boolean[ObjectSlot.values().length];

    private final Object2ObjectOpenHashMap<String, Object> dynamic = new Object2ObjectOpenHashMap<>();

    public boolean isEmpty() {
        return !hasAny(longPresent) && !hasAny(intPresent) && !hasAny(floatPresent)
            && !hasAny(booleanPresent) && !hasAny(objectPresent) && dynamic.isEmpty();
    }

    public boolean contains(String key) {
        if (key == null) {
            return false;
        }
        Slot slot = LOOKUP.get(key);
        if (slot == null) {
            return dynamic.containsKey(key);
        }
        return switch (slot.type) {
            case LONG -> longPresent[slot.index];
            case INT -> intPresent[slot.index];
            case FLOAT -> floatPresent[slot.index];
            case BOOLEAN -> booleanPresent[slot.index];
            case OBJECT -> objectPresent[slot.index];
        };
    }

    @Nullable
    public Object getRaw(String key) {
        if (key == null) {
            return null;
        }
        Slot slot = LOOKUP.get(key);
        if (slot == null) {
            return dynamic.get(key);
        }
        return switch (slot.type) {
            case LONG -> longPresent[slot.index] ? Long.valueOf(longValues[slot.index]) : null;
            case INT -> intPresent[slot.index] ? Integer.valueOf(intValues[slot.index]) : null;
            case FLOAT -> floatPresent[slot.index] ? Float.valueOf(floatValues[slot.index]) : null;
            case BOOLEAN -> booleanPresent[slot.index] ? Boolean.valueOf(booleanValues[slot.index]) : null;
            case OBJECT -> objectPresent[slot.index] ? objectValues[slot.index] : null;
        };
    }

    @Nullable
    public <T> T get(String key, Class<T> type) {
        Object value = getRaw(key);
        return castValue(value, type);
    }

    @Nullable
    public <T> T get(String key, Class<T> type, @Nullable T defaultValue) {
        T value = get(key, type);
        return value != null ? value : defaultValue;
    }

    @Nullable
    public Object put(String key, @Nullable Object value) {
        if (key == null) {
            return null;
        }
        if (value == null) {
            return remove(key);
        }
        Slot slot = LOOKUP.get(key);
        if (slot == null) {
            return dynamic.put(key, value);
        }
        return switch (slot.type) {
            case LONG -> setLong(slot.index, value);
            case INT -> setInt(slot.index, value);
            case FLOAT -> setFloat(slot.index, value);
            case BOOLEAN -> setBoolean(slot.index, value);
            case OBJECT -> setObject(slot.index, value);
        };
    }

    @Nullable
    public Object remove(String key) {
        if (key == null) {
            return null;
        }
        Slot slot = LOOKUP.get(key);
        if (slot == null) {
            return dynamic.remove(key);
        }
        return switch (slot.type) {
            case LONG -> clearSlot(longPresent, slot.index, Long.valueOf(longValues[slot.index]));
            case INT -> clearSlot(intPresent, slot.index, Integer.valueOf(intValues[slot.index]));
            case FLOAT -> clearSlot(floatPresent, slot.index, Float.valueOf(floatValues[slot.index]));
            case BOOLEAN -> clearSlot(booleanPresent, slot.index, Boolean.valueOf(booleanValues[slot.index]));
            case OBJECT -> clearObject(slot.index);
        };
    }

    public void clear() {
        clearPresence(longPresent);
        clearPresence(intPresent);
        clearPresence(floatPresent);
        clearPresence(booleanPresent);
        clearObjects();
        dynamic.clear();
    }

    public void forEach(BiConsumer<String, Object> consumer) {
        if (consumer == null) {
            return;
        }
        visit((key, value) -> {
            consumer.accept(key, value);
            return false;
        });
    }

    public boolean anyMatch(BiPredicate<String, Object> predicate) {
        if (predicate == null) {
            return false;
        }
        return visit(predicate);
    }

    public List<Map.Entry<String, Object>> entries() {
        List<Map.Entry<String, Object>> entries = new ArrayList<>();
        visit((key, value) -> {
            entries.add(Map.entry(key, value));
            return false;
        });
        return entries;
    }

    public Set<String> keySet() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        visit((key, value) -> {
            keys.add(key);
            return false;
        });
        return keys;
    }

    private Object setLong(int index, Object value) {
        long previous = longPresent[index] ? longValues[index] : 0L;
        boolean hadPrevious = longPresent[index];
        longValues[index] = asLong(value);
        longPresent[index] = true;
        return hadPrevious ? Long.valueOf(previous) : null;
    }

    private Object setInt(int index, Object value) {
        int previous = intPresent[index] ? intValues[index] : 0;
        boolean hadPrevious = intPresent[index];
        intValues[index] = asInt(value);
        intPresent[index] = true;
        return hadPrevious ? Integer.valueOf(previous) : null;
    }

    private Object setFloat(int index, Object value) {
        float previous = floatPresent[index] ? floatValues[index] : 0f;
        boolean hadPrevious = floatPresent[index];
        floatValues[index] = asFloat(value);
        floatPresent[index] = true;
        return hadPrevious ? Float.valueOf(previous) : null;
    }

    private Object setBoolean(int index, Object value) {
        boolean previous = booleanPresent[index] && booleanValues[index];
        boolean hadPrevious = booleanPresent[index];
        booleanValues[index] = asBoolean(value);
        booleanPresent[index] = true;
        return hadPrevious ? Boolean.valueOf(previous) : null;
    }

    private Object setObject(int index, Object value) {
        Object previous = objectPresent[index] ? objectValues[index] : null;
        objectValues[index] = value;
        objectPresent[index] = true;
        return previous;
    }

    private Object clearObject(int index) {
        if (!objectPresent[index]) {
            return null;
        }
        Object previous = objectValues[index];
        objectValues[index] = null;
        objectPresent[index] = false;
        return previous;
    }

    private static Object clearSlot(boolean[] presence, int index, Object previousValue) {
        if (!presence[index]) {
            return null;
        }
        presence[index] = false;
        return previousValue;
    }

    private static void clearPresence(boolean[] presence) {
        Arrays.fill(presence, false);
    }

    private void clearObjects() {
        for (int i = 0; i < objectValues.length; i++) {
            objectValues[i] = null;
            objectPresent[i] = false;
        }
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
            }
        }
        throw new IllegalArgumentException("Expected numeric value for long slot but received " + value.getClass().getName());
    }

    private static int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        throw new IllegalArgumentException("Expected numeric value for int slot but received " + value.getClass().getName());
    }

    private static float asFloat(Object value) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String string) {
            try {
                return Float.parseFloat(string);
            } catch (NumberFormatException ignored) {
            }
        }
        throw new IllegalArgumentException("Expected numeric value for float slot but received " + value.getClass().getName());
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        throw new IllegalArgumentException("Expected boolean-compatible value but received " + value.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private static <T> T castValue(@Nullable Object value, Class<T> type) {
        if (value == null || type == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        if (value instanceof Identifier identifier) {
            if (type == String.class) {
                return (T) identifier.toString();
            }
        }
        if (value instanceof BlockPos pos) {
            if (type == BlockPos.class) {
                return (T) pos;
            }
        }
        if (value instanceof Number number) {
            if (type == Long.class) {
                return (T) Long.valueOf(number.longValue());
            }
            if (type == Integer.class) {
                return (T) Integer.valueOf(number.intValue());
            }
            if (type == Float.class) {
                return (T) Float.valueOf(number.floatValue());
            }
        }
        if (value instanceof Boolean bool && type == Boolean.class) {
            return (T) bool;
        }
        return null;
    }

    private static boolean hasAny(boolean[] presence) {
        for (boolean flag : presence) {
            if (flag) {
                return true;
            }
        }
        return false;
    }

    private boolean visit(BiPredicate<String, Object> visitor) {
        for (int i = 0; i < longValues.length; i++) {
            if (longPresent[i] && visitor.test(LONG_KEYS[i], Long.valueOf(longValues[i]))) {
                return true;
            }
        }
        for (int i = 0; i < intValues.length; i++) {
            if (intPresent[i] && visitor.test(INT_KEYS[i], Integer.valueOf(intValues[i]))) {
                return true;
            }
        }
        for (int i = 0; i < floatValues.length; i++) {
            if (floatPresent[i] && visitor.test(FLOAT_KEYS[i], Float.valueOf(floatValues[i]))) {
                return true;
            }
        }
        for (int i = 0; i < booleanValues.length; i++) {
            if (booleanPresent[i] && visitor.test(BOOLEAN_KEYS[i], Boolean.valueOf(booleanValues[i]))) {
                return true;
            }
        }
        for (int i = 0; i < objectValues.length; i++) {
            if (objectPresent[i] && visitor.test(OBJECT_KEYS[i], objectValues[i])) {
                return true;
            }
        }
        ObjectIterator<Object2ObjectMap.Entry<String, Object>> iterator = dynamic.object2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Object2ObjectMap.Entry<String, Object> entry = iterator.next();
            if (visitor.test(entry.getKey(), entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static void registerLong(LongSlot slot, String key) {
        LOOKUP.put(key, new Slot(SlotType.LONG, slot.ordinal(), key));
        LONG_KEYS[slot.ordinal()] = key;
    }

    private static void registerInt(IntSlot slot, String key) {
        LOOKUP.put(key, new Slot(SlotType.INT, slot.ordinal(), key));
        INT_KEYS[slot.ordinal()] = key;
    }

    private static void registerFloat(FloatSlot slot, String key) {
        LOOKUP.put(key, new Slot(SlotType.FLOAT, slot.ordinal(), key));
        FLOAT_KEYS[slot.ordinal()] = key;
    }

    private static void registerBoolean(BooleanSlot slot, String key) {
        LOOKUP.put(key, new Slot(SlotType.BOOLEAN, slot.ordinal(), key));
        BOOLEAN_KEYS[slot.ordinal()] = key;
    }

    private static void registerObject(ObjectSlot slot, String key) {
        LOOKUP.put(key, new Slot(SlotType.OBJECT, slot.ordinal(), key));
        OBJECT_KEYS[slot.ordinal()] = key;
    }
}
