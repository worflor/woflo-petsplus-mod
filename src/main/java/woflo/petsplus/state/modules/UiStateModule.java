package woflo.petsplus.state.modules;

public interface UiStateModule extends DataBackedModule<UiStateModule.Data> {
    void triggerXpFlash(long currentTick);
    boolean hasActiveXpFlash(long currentTick);
    long getXpFlashStartTick();

    record Data(long xpFlashStartTick) {}
}
