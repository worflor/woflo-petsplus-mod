package woflo.petsplus.api.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.state.PetComponent;

/**
 * Fired when two vanilla animals finish breeding and a child has been spawned.
 * Consumers can listen to inherit additional metadata, grant bonuses or emit
 * custom feedback without adding more mixins.
 */
public final class PetBreedEvent {
    /** Global hook invoked each time a tracked breeding pair produces a child. */
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
        listeners -> context -> {
            for (Listener listener : listeners) {
                listener.onPetBred(context);
            }
        }
    );

    private PetBreedEvent() {
    }

    /** Dispatches the breeding event. */
    public static void fire(Context context) {
        EVENT.invoker().onPetBred(context);
    }

    /** Listener for breeding events. */
    @FunctionalInterface
    public interface Listener {
        void onPetBred(Context context);
    }

    /** Immutable context describing the breeding interaction. */
    public static final class Context {
        private final AnimalEntity primaryParent;
        private final AnimalEntity partner;
        private final PassiveEntity child;
        private final @Nullable PetComponent primaryComponent;
        private final @Nullable PetComponent partnerComponent;
        private final @Nullable PetComponent childComponent;
        private final boolean roleInherited;
        private final boolean characteristicsInherited;
        private final @Nullable BirthContext birthContext;
        private final @Nullable Identifier assignedNature;

        public Context(AnimalEntity primaryParent,
                       AnimalEntity partner,
                       PassiveEntity child,
                       @Nullable PetComponent primaryComponent,
                       @Nullable PetComponent partnerComponent,
                       @Nullable PetComponent childComponent,
                       boolean roleInherited,
                       boolean characteristicsInherited,
                       @Nullable BirthContext birthContext,
                       @Nullable Identifier assignedNature) {
            this.primaryParent = primaryParent;
            this.partner = partner;
            this.child = child;
            this.primaryComponent = primaryComponent;
            this.partnerComponent = partnerComponent;
            this.childComponent = childComponent;
            this.roleInherited = roleInherited;
            this.characteristicsInherited = characteristicsInherited;
            this.birthContext = birthContext;
            this.assignedNature = assignedNature;
        }

        public AnimalEntity getPrimaryParent() {
            return primaryParent;
        }

        public AnimalEntity getPartner() {
            return partner;
        }

        public PassiveEntity getChild() {
            return child;
        }

        public @Nullable PetComponent getPrimaryComponent() {
            return primaryComponent;
        }

        public @Nullable PetComponent getPartnerComponent() {
            return partnerComponent;
        }

        public @Nullable PetComponent getChildComponent() {
            return childComponent;
        }

        public boolean isRoleInherited() {
            return roleInherited;
        }

        public boolean isCharacteristicsInherited() {
            return characteristicsInherited;
        }

        public @Nullable BirthContext getBirthContext() {
            return birthContext;
        }

        public @Nullable Identifier getAssignedNature() {
            return assignedNature;
        }
    }

    /** Snapshot of the world context surrounding a newborn's spawn moment. */
    public static final class BirthContext {
        private final long worldTime;
        private final long timeOfDay;
        private final Identifier dimensionId;
        private final boolean indoors;
        private final boolean daytime;
        private final boolean raining;
        private final boolean thundering;
        private final int nearbyPlayerCount;
        private final int nearbyPetCount;
        private final boolean fullMoon;
        private final boolean primaryParentOwned;
        private final boolean partnerParentOwned;
        private final Environment environment;

        public BirthContext(long worldTime,
                            long timeOfDay,
                            Identifier dimensionId,
                            boolean indoors,
                            boolean daytime,
                            boolean raining,
                            boolean thundering,
                            int nearbyPlayerCount,
                            int nearbyPetCount,
                            boolean fullMoon,
                            boolean primaryParentOwned,
                            boolean partnerParentOwned,
                            Environment environment) {
            this.worldTime = worldTime;
            this.timeOfDay = timeOfDay;
            this.dimensionId = dimensionId;
            this.indoors = indoors;
            this.daytime = daytime;
            this.raining = raining;
            this.thundering = thundering;
            this.nearbyPlayerCount = nearbyPlayerCount;
            this.nearbyPetCount = nearbyPetCount;
            this.fullMoon = fullMoon;
            this.primaryParentOwned = primaryParentOwned;
            this.partnerParentOwned = partnerParentOwned;
            this.environment = environment;
        }

        public long getWorldTime() {
            return worldTime;
        }

        public long getTimeOfDay() {
            return timeOfDay;
        }

        public Identifier getDimensionId() {
            return dimensionId;
        }

        public boolean isIndoors() {
            return indoors;
        }

        public boolean isDaytime() {
            return daytime;
        }

        public boolean isRaining() {
            return raining;
        }

        public boolean isThundering() {
            return thundering;
        }

        public int getNearbyPlayerCount() {
            return nearbyPlayerCount;
        }

        public int getNearbyPetCount() {
            return nearbyPetCount;
        }

        public boolean isFullMoon() {
            return fullMoon;
        }

        public boolean isPrimaryParentOwned() {
            return primaryParentOwned;
        }

        public boolean isPartnerParentOwned() {
            return partnerParentOwned;
        }

        public Environment getEnvironment() {
            return environment;
        }

        /**
         * Immutable snapshot describing the biome and nearby block make-up where a pet was born.
         */
        public static final class Environment {
            private final net.minecraft.util.math.BlockPos position;
            private final Identifier biomeId;
            private final float biomeTemperature;
            private final boolean deepDarkBiome;
            private final boolean mushroomFieldsBiome;
            private final boolean oceanBiome;
            private final boolean swampBiome;
            private final boolean snowyPrecipitation;
            private final int skyLightLevel;
            private final int height;
            private final boolean hasOpenSky;
            private final boolean hasCozyBlocks;
            private final boolean hasValuableOres;
            private final boolean hasLushFoliage;
            private final boolean fullySubmerged;
            private final boolean nearLavaOrMagma;
            private final boolean nearPowderSnow;
            private final boolean nearMudOrMangrove;
            private final boolean nearMajorStructure;
            private final boolean hasArchaeologySite;
            private final boolean nearTrialChamber;
            private final boolean hasCherryBloom;
            private final boolean hasActiveRedstone;
            private final boolean hasHomesteadBlocks;
            private final boolean hasRecentCombat;
            private final int nearbyChestCount;
            private final int totalStorageItems;
            private final int uniqueItemTypes;
            private final int combatRelevantItems;

            public Environment(net.minecraft.util.math.BlockPos position,
                               Identifier biomeId,
                               float biomeTemperature,
                               boolean deepDarkBiome,
                               boolean mushroomFieldsBiome,
                               boolean oceanBiome,
                               boolean swampBiome,
                               boolean snowyPrecipitation,
                               int skyLightLevel,
                               int height,
                               boolean hasOpenSky,
                               boolean hasCozyBlocks,
                               boolean hasValuableOres,
                               boolean hasLushFoliage,
                               boolean fullySubmerged,
                               boolean nearLavaOrMagma,
                               boolean nearPowderSnow,
                               boolean nearMudOrMangrove,
                               boolean nearMajorStructure,
                               boolean hasArchaeologySite,
                               boolean nearTrialChamber,
                               boolean hasCherryBloom,
                               boolean hasActiveRedstone,
                               boolean hasHomesteadBlocks,
                               boolean hasRecentCombat,
                               int nearbyChestCount,
                               int totalStorageItems,
                               int uniqueItemTypes,
                               int combatRelevantItems) {
                this.position = position;
                this.biomeId = biomeId;
                this.biomeTemperature = biomeTemperature;
                this.deepDarkBiome = deepDarkBiome;
                this.mushroomFieldsBiome = mushroomFieldsBiome;
                this.oceanBiome = oceanBiome;
                this.swampBiome = swampBiome;
                this.snowyPrecipitation = snowyPrecipitation;
                this.skyLightLevel = skyLightLevel;
                this.height = height;
                this.hasOpenSky = hasOpenSky;
                this.hasCozyBlocks = hasCozyBlocks;
                this.hasValuableOres = hasValuableOres;
                this.hasLushFoliage = hasLushFoliage;
                this.fullySubmerged = fullySubmerged;
                this.nearLavaOrMagma = nearLavaOrMagma;
                this.nearPowderSnow = nearPowderSnow;
                this.nearMudOrMangrove = nearMudOrMangrove;
                this.nearMajorStructure = nearMajorStructure;
                this.hasArchaeologySite = hasArchaeologySite;
                this.nearTrialChamber = nearTrialChamber;
                this.hasCherryBloom = hasCherryBloom;
                this.hasActiveRedstone = hasActiveRedstone;
                this.hasHomesteadBlocks = hasHomesteadBlocks;
                this.hasRecentCombat = hasRecentCombat;
                this.nearbyChestCount = nearbyChestCount;
                this.totalStorageItems = totalStorageItems;
                this.uniqueItemTypes = uniqueItemTypes;
                this.combatRelevantItems = combatRelevantItems;
            }

            public net.minecraft.util.math.BlockPos getPosition() {
                return position;
            }

            public Identifier getBiomeId() {
                return biomeId;
            }

            public float getBiomeTemperature() {
                return biomeTemperature;
            }

            public boolean isDeepDarkBiome() {
                return deepDarkBiome;
            }

            public boolean isMushroomFieldsBiome() {
                return mushroomFieldsBiome;
            }

            public boolean isOceanBiome() {
                return oceanBiome;
            }

            public boolean isSwampBiome() {
                return swampBiome;
            }

            public boolean hasSnowyPrecipitation() {
                return snowyPrecipitation;
            }

            public int getSkyLightLevel() {
                return skyLightLevel;
            }

            public int getHeight() {
                return height;
            }

            public boolean hasOpenSky() {
                return hasOpenSky;
            }

            public boolean hasCozyBlocks() {
                return hasCozyBlocks;
            }

            public boolean hasValuableOres() {
                return hasValuableOres;
            }

            public boolean hasLushFoliage() {
                return hasLushFoliage;
            }

            public boolean isFullySubmerged() {
                return fullySubmerged;
            }

            public boolean isNearLavaOrMagma() {
                return nearLavaOrMagma;
            }

            public boolean isNearPowderSnow() {
                return nearPowderSnow;
            }

            public boolean isNearMudOrMangrove() {
                return nearMudOrMangrove;
            }

            public boolean isNearMajorStructure() {
                return nearMajorStructure;
            }

            public boolean hasArchaeologySite() {
                return hasArchaeologySite;
            }

            public boolean isNearTrialChamber() {
                return nearTrialChamber;
            }

            public boolean hasCherryBloom() {
                return hasCherryBloom;
            }

            public boolean hasActiveRedstone() {
                return hasActiveRedstone;
            }

            public boolean hasHomesteadBlocks() {
                return hasHomesteadBlocks;
            }

            public boolean hasRecentCombat() {
                return hasRecentCombat;
            }

            public int getNearbyChestCount() {
                return nearbyChestCount;
            }

            public int getTotalStorageItems() {
                return totalStorageItems;
            }

            public int getUniqueItemTypes() {
                return uniqueItemTypes;
            }

            public int getCombatRelevantItems() {
                return combatRelevantItems;
            }

            public float getOrganizationRatio() {
                if (totalStorageItems == 0) return 0f;
                return (float) uniqueItemTypes / (float) totalStorageItems;
            }
        }
    }
}
