package woflo.petsplus.state.modules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import woflo.petsplus.util.CodecUtils;

public interface ProgressionModule extends DataBackedModule<ProgressionModule.Data> {
    int getLevel();
    void setLevel(int level);
    long getExperience();
    void setExperience(long experience);
    void addExperience(long amount, ServerWorld world, long currentTick);
    long getXpForNextLevel();
    boolean hasMilestone(int id);
    void unlockMilestone(int id);
    Set<Integer> getUnlockedMilestones();
    boolean hasAbility(Identifier abilityId);
    void unlockAbility(Identifier abilityId);
    Set<Identifier> getUnlockedAbilities();
    Map<String, Float> getPermanentStatBoosts();
    void addPermanentStatBoost(String statName, float amount);
    float getPermanentStatBoost(String statName);
    void clearProgressionUnlocks();
    void setTributeMilestone(int level, Identifier itemId);
    @Nullable Identifier getTributeMilestone(int level);
    boolean hasTributeMilestone(int level);
    void registerLevelUpListener(Consumer<LevelUpEvent> listener);

    record Data(
        int level,
        long experience,
        Map<Integer, Boolean> unlockedMilestones,
        Map<Identifier, Boolean> unlockedAbilities,
        Map<String, Float> permanentStatBoosts,
        Map<Integer, Identifier> tributeMilestones
    ) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.INT.fieldOf("level").forGetter(Data::level),
                Codec.LONG.fieldOf("experience").forGetter(Data::experience),
                Codec.unboundedMap(Codec.INT, Codec.BOOL).optionalFieldOf("unlockedMilestones", new HashMap<>()).forGetter(Data::unlockedMilestones),
                Codec.unboundedMap(CodecUtils.identifierCodec(), Codec.BOOL).optionalFieldOf("unlockedAbilities", new HashMap<>()).forGetter(Data::unlockedAbilities),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("permanentStatBoosts", new HashMap<>()).forGetter(Data::permanentStatBoosts),
                Codec.unboundedMap(Codec.INT, CodecUtils.identifierCodec()).optionalFieldOf("tributeMilestones", new HashMap<>()).forGetter(Data::tributeMilestones)
            ).apply(instance, Data::new)
        );
    }

    record LevelUpEvent(int newLevel, long currentTick, ServerWorld world) {}
}
