package woflo.petsplus.state;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.SpawnReason;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.storage.ReadView;
import net.minecraft.util.ErrorReporter;
import woflo.petsplus.api.entity.PetsplusTameable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility that snapshots and restores pet state, including vanilla entity NBT and
 * the PetsPlus {@link PetComponent} payload, to enable import/export through chat commands.
 */
public final class PetSnapshot {

    private static final String KEY_VERSION = "version";
    private static final String KEY_ENTITY_TYPE = "entity_type";
    private static final String KEY_VANILLA = "vanilla";
    private static final String KEY_COMPONENT = "component";
    private static final int CURRENT_VERSION = 1;
    private static final Set<String> VANILLA_EXCLUDED_KEYS = Util.make(new HashSet<>(), keys -> {
        keys.add("Pos");
        keys.add("Motion");
        keys.add("Rotation");
        keys.add("UUID");
        keys.add("UUIDMost");
        keys.add("UUIDLeast");
        keys.add("Dimension");
        keys.add("WorldUUIDMost");
        keys.add("WorldUUIDLeast");
        keys.add("WorldUUID");
        keys.add("PortalCooldown");
        keys.add("LoveCause");
        keys.add("HurtBy");
        keys.add("AngryAt");
        keys.add("Leash");
        keys.add("Owner");
        keys.add("OwnerUUID");
    });

    private PetSnapshot() {
    }

    /**
     * Capture all mod-owned and vanilla state for the supplied pet and encode it as a Base64 payload.
     */
    public static String exportToString(MobEntity pet) throws SnapshotException {
        Identifier typeId = Registries.ENTITY_TYPE.getId(pet.getType());
        if (typeId == null) {
            throw new SnapshotException("Unable to resolve entity type for export");
        }

        NbtCompound root = new NbtCompound();
        root.putInt(KEY_VERSION, CURRENT_VERSION);
        root.putString(KEY_ENTITY_TYPE, typeId.toString());

        NbtCompound vanilla = captureVanillaState(pet);
        root.put(KEY_VANILLA, vanilla);

        PetComponent component = PetComponent.get(pet);
        if (component == null) {
            component = PetComponent.getOrCreate(pet);
        }
        NbtCompound componentPayload = new NbtCompound();
        component.writeToNbt(componentPayload);
        componentPayload.remove("petUuid");
        root.put(KEY_COMPONENT, componentPayload);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            NbtIo.writeCompressed(root, output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException e) {
            throw new SnapshotException("Failed to encode pet snapshot", e);
        }
    }

    /**
     * Recreate a pet from the supplied payload and place it in front of the player.
     */
    public static MobEntity importFromString(String payload, ServerPlayerEntity player) throws SnapshotException {
        SnapshotData data = decode(payload);

        ServerWorld world = player.getWorld();
        EntityType<?> entityType = Registries.ENTITY_TYPE.get(data.entityTypeId());
        if (entityType == null) {
            throw new SnapshotException("Unknown entity type: " + data.entityTypeId());
        }
        Entity created = entityType.create(world, SpawnReason.COMMAND);
        if (!(created instanceof MobEntity pet)) {
            throw new SnapshotException("Snapshot entity type is not a mob: " + data.entityTypeId());
        }

        Vec3d spawnPos = positionInFrontOfPlayer(player, 1.5);
        pet.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, player.getYaw(), player.getPitch());

        NbtCompound vanillaData = data.vanillaNbt().copy();
        RegistryWrapper.WrapperLookup registryLookup = world.getRegistryManager();
        ReadView readView = NbtReadView.create(ErrorReporter.EMPTY, registryLookup, vanillaData);
        pet.readData(readView);
        pet.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, player.getYaw(), player.getPitch());

        if (!world.spawnEntity(pet)) {
            throw new SnapshotException("World rejected spawned pet instance");
        }

        PetComponent component = StateManager.forWorld(world).getPetComponent(pet);
        component.readFromNbt(data.componentNbt().copy());
        component.setOwner(player);
        component.ensureCharacteristics();
        component.refreshSpeciesDescriptor();

        applyTameBridge(pet, player);

        return pet;
    }

    private static void applyTameBridge(MobEntity pet, ServerPlayerEntity owner) {
        if (pet instanceof PetsplusTameable tameable) {
            tameable.petsplus$setOwner(owner);
            tameable.petsplus$setTamed(true);
        }
    }

    private static Vec3d positionInFrontOfPlayer(ServerPlayerEntity player, double distance) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d base = player.getPos().add(look.multiply(distance));
        return new Vec3d(base.x, player.getY(), base.z);
    }

    private static NbtCompound captureVanillaState(MobEntity pet) {
        if (!(pet.getWorld() instanceof ServerWorld serverWorld)) {
            return new NbtCompound();
        }

        RegistryWrapper.WrapperLookup registryLookup = serverWorld.getRegistryManager();
        NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY, registryLookup);
        pet.saveSelfData(writeView);
        NbtCompound vanilla = writeView.getNbt();
        for (String key : VANILLA_EXCLUDED_KEYS) {
            vanilla.remove(key);
        }
        return vanilla;
    }

    private static SnapshotData decode(String payload) throws SnapshotException {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new SnapshotException("Snapshot blob is not valid Base64", e);
        }

        NbtCompound root;
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            root = NbtIo.readCompressed(input, NbtSizeTracker.ofUnlimitedBytes());
        } catch (IOException e) {
            throw new SnapshotException("Unable to decompress snapshot data", e);
        }

        int version = root.contains(KEY_VERSION)
            ? root.getInt(KEY_VERSION).orElse(0)
            : 0;
        if (version > CURRENT_VERSION) {
            throw new SnapshotException("Snapshot was created with a newer version (" + version + ")");
        }

        String typeString = root.getString(KEY_ENTITY_TYPE).orElse("");
        if (typeString == null || typeString.isEmpty()) {
            throw new SnapshotException("Snapshot is missing entity type information");
        }

        Identifier typeId = Identifier.tryParse(typeString);
        if (typeId == null) {
            throw new SnapshotException("Snapshot contains invalid entity type: " + typeString);
        }

        NbtCompound vanilla = root.getCompound(KEY_VANILLA)
            .map(NbtCompound::copy)
            .orElseGet(NbtCompound::new);
        NbtCompound component = root.getCompound(KEY_COMPONENT)
            .map(NbtCompound::copy)
            .orElseGet(NbtCompound::new);

        component.remove("petUuid");
        component.getCompound("stateData").ifPresent(state -> state.remove("petsplus:owner_uuid"));

        return new SnapshotData(typeId, vanilla, component);
    }

    public record SnapshotData(Identifier entityTypeId, NbtCompound vanillaNbt, NbtCompound componentNbt) {
    }

    public static class SnapshotException extends Exception {
        public SnapshotException(String message) {
            super(message);
        }

        public SnapshotException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

