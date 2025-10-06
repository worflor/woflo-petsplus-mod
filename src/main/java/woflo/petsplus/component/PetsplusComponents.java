package woflo.petsplus.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.ComponentType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import woflo.petsplus.state.gossip.PetGossipLedger;
import woflo.petsplus.state.modules.*;
import woflo.petsplus.util.CodecUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Component types for persistent data storage using Minecraft's component system.
 */
public class PetsplusComponents {
    
    // Pet component data
    public static final ComponentType<PetData> PET_DATA = register(
        "pet_data",
        ComponentType.<PetData>builder()
            .codec(PetData.CODEC)
            .packetCodec(PetData.PACKET_CODEC)
            .build()
    );
    
    // Owner combat state component data
    public static final ComponentType<OwnerCombatData> OWNER_COMBAT_DATA = register(
        "owner_combat_data", 
        ComponentType.<OwnerCombatData>builder()
            .codec(OwnerCombatData.CODEC)
            .packetCodec(OwnerCombatData.PACKET_CODEC)
            .build()
    );
    
    // Respec token component for ability respecialization
    public static final ComponentType<RespecData> RESPEC_TOKEN = register(
        "respec_token",
        ComponentType.<RespecData>builder()
            .codec(RespecData.CODEC)
            .packetCodec(RespecData.PACKET_CODEC)
            .build()
    );
    
    // Linked whistle component for pet recall
    public static final ComponentType<LinkedWhistleData> LINKED_WHISTLE = register(
        "linked_whistle",
        ComponentType.<LinkedWhistleData>builder()
            .codec(LinkedWhistleData.CODEC)
            .packetCodec(LinkedWhistleData.PACKET_CODEC)
            .build()
    );
    
    // Pet metadata component for enhanced pet tracking
    public static final ComponentType<PetMetadata> PET_METADATA = register(
        "pet_metadata",
        ComponentType.<PetMetadata>builder()
            .codec(PetMetadata.CODEC)
            .packetCodec(PetMetadata.PACKET_CODEC)
            .build()
    );
    
    // Proof of Existence memorial component
    public static final ComponentType<PoeData> POE_MEMORIAL = register(
        "poe_memorial",
        ComponentType.<PoeData>builder()
            .codec(PoeData.CODEC)
            .packetCodec(PoeData.PACKET_CODEC)
            .build()
    );
    
    /**
     * Pet component persistent data with full module support.
     */
    public record PetData(
        Optional<Identifier> role,
        Map<String, Long> cooldowns,
        long lastAttackTick,
        boolean isPerched,
        long xpFlashStartTick,
        Optional<ProgressionModule.Data> progression,
        Optional<HistoryModule.Data> history,
        Optional<InventoryModule.Data> inventories,
        Optional<OwnerModule.Data> owner,
        Optional<SchedulingModule.Data> scheduling,
        Optional<CharacteristicsModule.Data> characteristics,
        Optional<PetGossipLedger> gossip,
        Optional<NbtCompound> mood,
        Optional<NbtCompound> stateData,
        int schemaVersion
    ) {
        public static final int CURRENT_SCHEMA_VERSION = 7;

        public static final Codec<PetData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                CodecUtils.identifierCodec().optionalFieldOf("role").forGetter(PetData::role),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", new HashMap<>()).forGetter(PetData::cooldowns),
                Codec.LONG.optionalFieldOf("lastAttackTick", 0L).forGetter(PetData::lastAttackTick),
                Codec.BOOL.optionalFieldOf("isPerched", false).forGetter(PetData::isPerched),
                Codec.LONG.optionalFieldOf("xpFlashStartTick", -1L).forGetter(PetData::xpFlashStartTick),
                ProgressionModule.Data.CODEC.optionalFieldOf("progression").forGetter(PetData::progression),
                HistoryModule.Data.CODEC.optionalFieldOf("history").forGetter(PetData::history),
                InventoryModule.Data.CODEC.optionalFieldOf("inventories").forGetter(PetData::inventories),
                OwnerModule.Data.CODEC.optionalFieldOf("owner").forGetter(PetData::owner),
                SchedulingModule.Data.CODEC.optionalFieldOf("scheduling").forGetter(PetData::scheduling),
                CharacteristicsModule.Data.CODEC.optionalFieldOf("characteristics").forGetter(PetData::characteristics),
                PetGossipLedger.CODEC.optionalFieldOf("gossipLedger").forGetter(PetData::gossip),
                NbtCompound.CODEC.optionalFieldOf("mood").forGetter(PetData::mood),
                NbtCompound.CODEC.optionalFieldOf("stateData").forGetter(PetData::stateData),
                Codec.INT.optionalFieldOf("schemaVersion", 1).forGetter(PetData::schemaVersion)
            ).apply(instance, PetData::new)
        );

        private static final PacketCodec<RegistryByteBuf, ProgressionModule.Data> PROGRESSION_PACKET_CODEC =
            nbtBackedPacketCodec(ProgressionModule.Data.CODEC, "progression module");
        private static final PacketCodec<RegistryByteBuf, HistoryModule.Data> HISTORY_PACKET_CODEC =
            nbtBackedPacketCodec(HistoryModule.Data.CODEC, "history module");
        private static final PacketCodec<RegistryByteBuf, InventoryModule.Data> INVENTORY_PACKET_CODEC =
            nbtBackedPacketCodec(InventoryModule.Data.CODEC, "inventory module");
        private static final PacketCodec<RegistryByteBuf, OwnerModule.Data> OWNER_PACKET_CODEC =
            nbtBackedPacketCodec(OwnerModule.Data.CODEC, "owner module");
        private static final PacketCodec<RegistryByteBuf, SchedulingModule.Data> SCHEDULING_PACKET_CODEC =
            nbtBackedPacketCodec(SchedulingModule.Data.CODEC, "scheduling module");
        private static final PacketCodec<RegistryByteBuf, CharacteristicsModule.Data> CHARACTERISTICS_PACKET_CODEC =
            nbtBackedPacketCodec(CharacteristicsModule.Data.CODEC, "characteristics module");
        private static final PacketCodec<RegistryByteBuf, PetGossipLedger> GOSSIP_PACKET_CODEC =
            nbtBackedPacketCodec(PetGossipLedger.CODEC, "gossip ledger");
        private static final PacketCodec<RegistryByteBuf, NbtCompound> MOOD_PACKET_CODEC =
            nbtBackedPacketCodec(NbtCompound.CODEC, "mood payload");
        private static final PacketCodec<RegistryByteBuf, HashMap<String, Long>> COOLDOWN_MAP_PACKET_CODEC =
            PacketCodecs.map(HashMap::new, PacketCodecs.STRING, PacketCodecs.VAR_LONG);
        private static final PacketCodec<RegistryByteBuf, NbtCompound> NBT_PACKET_CODEC = PacketCodec.ofStatic(
            (buf, compound) -> buf.writeNbt(compound == null ? new NbtCompound() : compound.copy()),
            buf -> {
                NbtCompound compound = buf.readNbt();
                return compound == null ? new NbtCompound() : compound;
            }
        );

        public static final PacketCodec<RegistryByteBuf, PetData> PACKET_CODEC = PacketCodec.ofStatic(
            (buf, data) -> {
                PacketCodecs.optional(PacketCodecs.STRING).encode(buf, data.role().map(Identifier::toString));
                COOLDOWN_MAP_PACKET_CODEC.encode(buf, new HashMap<>(data.cooldowns()));
                PacketCodecs.VAR_LONG.encode(buf, data.lastAttackTick());
                PacketCodecs.BOOLEAN.encode(buf, data.isPerched());
                PacketCodecs.VAR_LONG.encode(buf, data.xpFlashStartTick());
                PacketCodecs.optional(PROGRESSION_PACKET_CODEC).encode(buf, data.progression());
                PacketCodecs.optional(HISTORY_PACKET_CODEC).encode(buf, data.history());
                PacketCodecs.optional(INVENTORY_PACKET_CODEC).encode(buf, data.inventories());
                PacketCodecs.optional(OWNER_PACKET_CODEC).encode(buf, data.owner());
                PacketCodecs.optional(SCHEDULING_PACKET_CODEC).encode(buf, data.scheduling());
                PacketCodecs.optional(CHARACTERISTICS_PACKET_CODEC).encode(buf, data.characteristics());
                PacketCodecs.optional(GOSSIP_PACKET_CODEC).encode(buf, data.gossip());
                PacketCodecs.optional(MOOD_PACKET_CODEC).encode(buf, data.mood());
                PacketCodecs.optional(NBT_PACKET_CODEC).encode(buf, data.stateData());
                PacketCodecs.VAR_INT.encode(buf, data.schemaVersion());
            },
            buf -> {
                Optional<String> roleId = PacketCodecs.optional(PacketCodecs.STRING).decode(buf);
                Map<String, Long> cooldowns = COOLDOWN_MAP_PACKET_CODEC.decode(buf);
                long lastAttackTick = PacketCodecs.VAR_LONG.decode(buf);
                boolean isPerched = PacketCodecs.BOOLEAN.decode(buf);
                long xpFlashStartTick = PacketCodecs.VAR_LONG.decode(buf);
                Optional<ProgressionModule.Data> progression = PacketCodecs.optional(PROGRESSION_PACKET_CODEC).decode(buf);
                Optional<HistoryModule.Data> history = PacketCodecs.optional(HISTORY_PACKET_CODEC).decode(buf);
                Optional<InventoryModule.Data> inventories = PacketCodecs.optional(INVENTORY_PACKET_CODEC).decode(buf);
                Optional<OwnerModule.Data> owner = PacketCodecs.optional(OWNER_PACKET_CODEC).decode(buf);
                Optional<SchedulingModule.Data> scheduling = PacketCodecs.optional(SCHEDULING_PACKET_CODEC).decode(buf);
                Optional<CharacteristicsModule.Data> characteristics = PacketCodecs.optional(CHARACTERISTICS_PACKET_CODEC).decode(buf);
                Optional<PetGossipLedger> gossip = PacketCodecs.optional(GOSSIP_PACKET_CODEC).decode(buf);
                Optional<NbtCompound> mood = PacketCodecs.optional(MOOD_PACKET_CODEC).decode(buf);
                Optional<NbtCompound> stateData = PacketCodecs.optional(NBT_PACKET_CODEC).decode(buf);
                int schemaVersion = PacketCodecs.VAR_INT.decode(buf);

                return new PetData(roleId.map(Identifier::of), cooldowns, lastAttackTick, isPerched,
                    xpFlashStartTick, progression, history, inventories, owner, scheduling, characteristics, gossip, mood, stateData, schemaVersion);
            }
        );

        private static <T> PacketCodec<RegistryByteBuf, T> nbtBackedPacketCodec(Codec<T> codec, String debugName) {
            return PacketCodec.ofStatic(
                (buf, value) -> {
                    if (value == null) {
                        buf.writeNbt(new NbtCompound());
                        return;
                    }
                    var encoded = codec.encodeStart(NbtOps.INSTANCE, value);
                    var maybeElement = encoded.result();
                    if (maybeElement.isEmpty()) {
                        String detail = encoded.error().map(Object::toString).orElse("unknown error");
                        throw new IllegalStateException("Failed to encode " + debugName + ": " + detail);
                    }
                    if (!(maybeElement.get() instanceof NbtCompound compound)) {
                        throw new IllegalStateException("Expected NbtCompound when encoding " + debugName + ", got " + maybeElement.get());
                    }
                    buf.writeNbt(compound);
                },
                buf -> {
                    NbtCompound compound = buf.readNbt();
                    NbtCompound payload = compound == null ? new NbtCompound() : compound;
                    var decoded = codec.parse(NbtOps.INSTANCE, payload);
                    var maybeValue = decoded.result();
                    if (maybeValue.isEmpty()) {
                        String detail = decoded.error().map(Object::toString).orElse("unknown error");
                        throw new IllegalStateException("Failed to decode " + debugName + ": " + detail);
                    }
                    return maybeValue.get();
                }
            );
        }

        public static PetData empty() {
            return new PetData(Optional.empty(), Map.of(), 0, false, -1L,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), CURRENT_SCHEMA_VERSION);
        }

        public PetData withRole(Identifier roleId) {
            return new PetData(Optional.of(roleId), cooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, history, inventories, owner, scheduling, characteristics, gossip, mood, stateData, schemaVersion);
        }

        public PetData withCooldown(String key, long value) {
            Map<String, Long> newCooldowns = new java.util.HashMap<>(cooldowns);
            newCooldowns.put(key, value);
            return new PetData(role, newCooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, history, inventories, owner, scheduling, characteristics, gossip, mood, stateData, schemaVersion);
        }

        public PetData withLastAttackTick(long tick) {
            return new PetData(role, cooldowns, tick, isPerched, xpFlashStartTick, progression, history, inventories, owner, scheduling, characteristics, gossip, mood, stateData, schemaVersion);
        }

        public PetData withPerched(boolean perched) {
            return new PetData(role, cooldowns, lastAttackTick, perched, xpFlashStartTick, progression, history, inventories, owner, scheduling, characteristics, gossip, mood, stateData, schemaVersion);
        }

        public PetData withXpFlashStartTick(long tick) {
            return new PetData(role, cooldowns, lastAttackTick, isPerched, tick, progression, history, inventories, owner, scheduling, characteristics, gossip, mood, stateData, schemaVersion);
        }

        public PetData withProgression(ProgressionModule.Data data) {
            return new PetData(role, cooldowns, lastAttackTick, isPerched, xpFlashStartTick, Optional.of(data), history, inventories, owner, scheduling, characteristics, gossip, mood, stateData, schemaVersion);
        }

        public PetData withHistory(HistoryModule.Data data) {
            return new PetData(role, cooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, Optional.of(data), inventories, owner, scheduling, characteristics, gossip, mood, stateData, schemaVersion);
        }

        public PetData withInventories(InventoryModule.Data data) {
            return new PetData(role, cooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, history, Optional.of(data), owner, scheduling, characteristics, gossip, mood, stateData, schemaVersion);
        }

        public PetData withOwner(OwnerModule.Data data) {
            return new PetData(role, cooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, history, inventories, Optional.of(data), scheduling, characteristics, gossip, mood, stateData, schemaVersion);
        }

        public PetData withScheduling(SchedulingModule.Data data) {
            return new PetData(role, cooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, history, inventories, owner, Optional.of(data), characteristics, gossip, mood, stateData, schemaVersion);
        }

        public PetData withCharacteristics(CharacteristicsModule.Data data) {
            return new PetData(role, cooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, history, inventories, owner, scheduling, Optional.of(data), gossip, mood, stateData, schemaVersion);
        }

        public PetData withStateData(NbtCompound data) {
            return new PetData(role, cooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, history, inventories, owner, scheduling, characteristics, gossip, mood, Optional.of(data), schemaVersion);
        }

        public PetData withGossip(PetGossipLedger ledger) {
            if (ledger == null || ledger.isEmpty()) {
                return new PetData(role, cooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, history, inventories, owner, scheduling, characteristics, Optional.empty(), mood, stateData, schemaVersion);
            }
            PetGossipLedger snapshot = new PetGossipLedger();
            snapshot.copyFrom(ledger);
            return new PetData(role, cooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, history, inventories, owner, scheduling, characteristics, Optional.of(snapshot), mood, stateData, schemaVersion);
        }

        public PetData withMood(NbtCompound moodData) {
            NbtCompound copy = moodData == null ? new NbtCompound() : moodData.copy();
            if (copy.getKeys().isEmpty()) {
                return new PetData(role, cooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, history, inventories, owner, scheduling, characteristics, gossip, Optional.empty(), stateData, schemaVersion);
            }
            return new PetData(role, cooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, history, inventories, owner, scheduling, characteristics, gossip, Optional.of(copy), stateData, schemaVersion);
        }

        public PetData withSchemaVersion(int version) {
            return new PetData(role, cooldowns, lastAttackTick, isPerched, xpFlashStartTick, progression, history, inventories, owner, scheduling, characteristics, gossip, mood, stateData, version);
        }
    }
    
    /**
     * Owner combat state persistent data.
     */
    public record OwnerCombatData(
        boolean inCombat,
        long combatEndTick,
        long lastHitTick,
        long lastHitTakenTick,
        Map<String, Long> tempState
    ) {
        public static final Codec<OwnerCombatData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.BOOL.fieldOf("inCombat").forGetter(OwnerCombatData::inCombat),
                Codec.LONG.fieldOf("combatEndTick").forGetter(OwnerCombatData::combatEndTick),
                Codec.LONG.fieldOf("lastHitTick").forGetter(OwnerCombatData::lastHitTick),
                Codec.LONG.fieldOf("lastHitTakenTick").forGetter(OwnerCombatData::lastHitTakenTick),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("tempState").forGetter(OwnerCombatData::tempState)
            ).apply(instance, OwnerCombatData::new)
        );
        
        public static final PacketCodec<RegistryByteBuf, OwnerCombatData> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, OwnerCombatData::inCombat,
            PacketCodecs.VAR_LONG, OwnerCombatData::combatEndTick,
            PacketCodecs.VAR_LONG, OwnerCombatData::lastHitTick,
            PacketCodecs.VAR_LONG, OwnerCombatData::lastHitTakenTick,
            PacketCodecs.map(HashMap::new, PacketCodecs.STRING, PacketCodecs.VAR_LONG), OwnerCombatData::tempState,
            OwnerCombatData::new
        );
        
        public static OwnerCombatData empty() {
            return new OwnerCombatData(false, 0, 0, 0, Map.of());
        }
        
        public OwnerCombatData withCombat(boolean inCombat, long combatEndTick) {
            return new OwnerCombatData(inCombat, combatEndTick, lastHitTick, lastHitTakenTick, tempState);
        }
        
        public OwnerCombatData withLastHit(long tick) {
            return new OwnerCombatData(inCombat, combatEndTick, tick, lastHitTakenTick, tempState);
        }
        
        public OwnerCombatData withLastHitTaken(long tick) {
            return new OwnerCombatData(inCombat, combatEndTick, lastHitTick, tick, tempState);
        }
        
        public OwnerCombatData withTempState(String key, long value) {
            Map<String, Long> newTempState = new java.util.HashMap<>(tempState);
            newTempState.put(key, value);
            return new OwnerCombatData(inCombat, combatEndTick, lastHitTick, lastHitTakenTick, newTempState);
        }
    }
    
    /**
     * Respec token component data for ability respecialization.
     */
    public record RespecData(
        boolean enabled,
        Optional<String> targetRole,
        long createdTick
    ) {
        public static final Codec<RespecData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.BOOL.fieldOf("enabled").forGetter(RespecData::enabled),
                Codec.STRING.optionalFieldOf("targetRole").forGetter(RespecData::targetRole),
                Codec.LONG.fieldOf("createdTick").forGetter(RespecData::createdTick)
            ).apply(instance, RespecData::new)
        );
        
        public static final PacketCodec<RegistryByteBuf, RespecData> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, RespecData::enabled,
            PacketCodecs.optional(PacketCodecs.STRING), RespecData::targetRole,
            PacketCodecs.VAR_LONG, RespecData::createdTick,
            RespecData::new
        );
        
        public static RespecData create() {
            return new RespecData(true, Optional.empty(), 0);
        }
        
        public static RespecData forRole(String roleKey) {
            return new RespecData(true, Optional.of(roleKey), 0);
        }
    }
    
    /**
     * Linked whistle component data for pet recall.
     */
    public record LinkedWhistleData(
        Optional<UUID> linkedPetUuid,
        Optional<String> petName,
        long cooldownExpiry,
        int usesRemaining
    ) {
        public static final Codec<LinkedWhistleData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                net.minecraft.util.Uuids.CODEC.optionalFieldOf("linkedPetUuid").forGetter(LinkedWhistleData::linkedPetUuid),
                Codec.STRING.optionalFieldOf("petName").forGetter(LinkedWhistleData::petName),
                Codec.LONG.fieldOf("cooldownExpiry").forGetter(LinkedWhistleData::cooldownExpiry),
                Codec.INT.fieldOf("usesRemaining").forGetter(LinkedWhistleData::usesRemaining)
            ).apply(instance, LinkedWhistleData::new)
        );
        
        public static final PacketCodec<RegistryByteBuf, LinkedWhistleData> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.optional(PacketCodecs.string(36).xmap(UUID::fromString, UUID::toString)), LinkedWhistleData::linkedPetUuid,
            PacketCodecs.optional(PacketCodecs.STRING), LinkedWhistleData::petName,
            PacketCodecs.VAR_LONG, LinkedWhistleData::cooldownExpiry,
            PacketCodecs.VAR_INT, LinkedWhistleData::usesRemaining,
            LinkedWhistleData::new
        );
        
        public static LinkedWhistleData create(UUID petUuid, String petName) {
            return new LinkedWhistleData(Optional.of(petUuid), Optional.of(petName), 0, 10);
        }
        
        public LinkedWhistleData withCooldown(long expiry) {
            return new LinkedWhistleData(linkedPetUuid, petName, expiry, usesRemaining);
        }
        
        public LinkedWhistleData useOnce() {
            return new LinkedWhistleData(linkedPetUuid, petName, cooldownExpiry, Math.max(0, usesRemaining - 1));
        }
    }
    
    /**
     * Pet metadata component for enhanced pet tracking and display.
     */
    public record PetMetadata(
        Optional<String> customDisplayName,
        long bondStrength,
        Map<String, String> customTags,
        boolean isSpecial
    ) {
        public static final Codec<PetMetadata> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("customDisplayName").forGetter(PetMetadata::customDisplayName),
                Codec.LONG.fieldOf("bondStrength").forGetter(PetMetadata::bondStrength),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("customTags").forGetter(PetMetadata::customTags),
                Codec.BOOL.fieldOf("isSpecial").forGetter(PetMetadata::isSpecial)
            ).apply(instance, PetMetadata::new)
        );
        
        public static final PacketCodec<RegistryByteBuf, PetMetadata> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.optional(PacketCodecs.STRING), PetMetadata::customDisplayName,
            PacketCodecs.VAR_LONG, PetMetadata::bondStrength,
            PacketCodecs.map(HashMap::new, PacketCodecs.STRING, PacketCodecs.STRING), PetMetadata::customTags,
            PacketCodecs.BOOLEAN, PetMetadata::isSpecial,
            PetMetadata::new
        );
        
        public static PetMetadata create() {
            return new PetMetadata(Optional.empty(), 0, Map.of(), false);
        }
        
        public PetMetadata withDisplayName(String name) {
            return new PetMetadata(Optional.of(name), bondStrength, customTags, isSpecial);
        }
        
        public PetMetadata withBondStrength(long strength) {
            return new PetMetadata(customDisplayName, strength, customTags, isSpecial);
        }
        
        public PetMetadata withTag(String key, String value) {
            Map<String, String> newTags = new HashMap<>(customTags);
            newTags.put(key, value);
            return new PetMetadata(customDisplayName, bondStrength, newTags, isSpecial);
        }
        
        public PetMetadata withSpecial(boolean special) {
            return new PetMetadata(customDisplayName, bondStrength, customTags, special);
        }
    }
    
    /**
     * Proof of Existence memorial data for deceased pets.
     */
    public record PoeData(
        String petName,
        String petType,
        String role,
        int level,
        int experience,
        String timestamp
    ) {
        public static final Codec<PoeData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.STRING.fieldOf("petName").forGetter(PoeData::petName),
                Codec.STRING.fieldOf("petType").forGetter(PoeData::petType),
                Codec.STRING.fieldOf("role").forGetter(PoeData::role),
                Codec.INT.fieldOf("level").forGetter(PoeData::level),
                Codec.INT.fieldOf("experience").forGetter(PoeData::experience),
                Codec.STRING.fieldOf("timestamp").forGetter(PoeData::timestamp)
            ).apply(instance, PoeData::new)
        );
        
        public static final PacketCodec<RegistryByteBuf, PoeData> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, PoeData::petName,
            PacketCodecs.STRING, PoeData::petType,
            PacketCodecs.STRING, PoeData::role,
            PacketCodecs.VAR_INT, PoeData::level,
            PacketCodecs.VAR_INT, PoeData::experience,
            PacketCodecs.STRING, PoeData::timestamp,
            PoeData::new
        );
    }
    
    private static <T> ComponentType<T> register(String id, ComponentType<T> componentType) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, Identifier.of("petsplus", id), componentType);
    }
    
    /**
     * Initialize component types. Call this during mod initialization.
     */
    public static void register() {
        // Component types are registered via static initialization
    }
}