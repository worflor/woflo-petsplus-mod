package woflo.petsplus.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.component.ComponentType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

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
    
    /**
     * Pet component persistent data.
     */
    public record PetData(
        Optional<String> role,
        Map<String, Long> cooldowns,
        long lastAttackTick,
        boolean isPerched
    ) {
        public static final Codec<PetData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.STRING.optionalFieldOf("role").forGetter(PetData::role),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("cooldowns").forGetter(PetData::cooldowns),
                Codec.LONG.fieldOf("lastAttackTick").forGetter(PetData::lastAttackTick),
                Codec.BOOL.fieldOf("isPerched").forGetter(PetData::isPerched)
            ).apply(instance, PetData::new)
        );
        
        public static final PacketCodec<RegistryByteBuf, PetData> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.optional(PacketCodecs.STRING), PetData::role,
            PacketCodecs.map(HashMap::new, PacketCodecs.STRING, PacketCodecs.VAR_LONG), PetData::cooldowns,
            PacketCodecs.VAR_LONG, PetData::lastAttackTick,
            PacketCodecs.BOOLEAN, PetData::isPerched,
            PetData::new
        );
        
        public static PetData empty() {
            return new PetData(Optional.empty(), Map.of(), 0, false);
        }
        
        public PetData withRole(String roleKey) {
            return new PetData(Optional.of(roleKey), cooldowns, lastAttackTick, isPerched);
        }
        
        public PetData withCooldown(String key, long value) {
            Map<String, Long> newCooldowns = new java.util.HashMap<>(cooldowns);
            newCooldowns.put(key, value);
            return new PetData(role, newCooldowns, lastAttackTick, isPerched);
        }
        
        public PetData withLastAttackTick(long tick) {
            return new PetData(role, cooldowns, tick, isPerched);
        }
        
        public PetData withPerched(boolean perched) {
            return new PetData(role, cooldowns, lastAttackTick, perched);
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