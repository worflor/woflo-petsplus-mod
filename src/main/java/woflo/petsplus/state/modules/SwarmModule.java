package woflo.petsplus.state.modules;

import woflo.petsplus.state.coordination.PetSwarmIndex;

public interface SwarmModule extends PetModule {
    SwarmStateSnapshot snapshot();
    boolean updateIfMoved(PetSwarmIndex index);
    void applySwarmUpdate(PetSwarmIndex index, long cellKey, double x, double y, double z);
    void setFollowSpacingSample(double offsetX, double offsetZ, float padding, long sampleTick);
    FollowSpacingState getFollowSpacingState();

    record SwarmStateSnapshot(boolean initialized, long cellKey, double x, double y, double z) {}

    record FollowSpacingState(double offsetX, double offsetZ, float padding, long sampleTick) {}
}
