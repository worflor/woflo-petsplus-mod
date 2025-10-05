package woflo.petsplus.state.modules;

public interface PerchStateModule extends DataBackedModule<PerchStateModule.Data> {
    boolean isPerched();
    void setPerched(boolean perched, long currentTick);
    boolean recentlyPerched(long currentTick);

    record Data(boolean perched, long lastPerchTick) {}
}
