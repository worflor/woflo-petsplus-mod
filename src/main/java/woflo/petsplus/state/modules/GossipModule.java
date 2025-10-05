package woflo.petsplus.state.modules;

import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.Identifier;
import woflo.petsplus.state.gossip.RumorEntry;

public interface GossipModule extends DataBackedModule<GossipModule.Data> {
    void recordRumor(Identifier topicId, float intensity, float confidence, long currentTick,
                     @Nullable UUID sourceUuid, boolean paraphrased, boolean witnessed);
    void tick(long currentTick);
    boolean hasPendingShare(long currentTick);
    List<RumorEntry> drainRumors(int limit, long currentTick);

    record Data(List<RumorEntry> rumors, long optOutUntilTick, int clusterCursor) {}
}
