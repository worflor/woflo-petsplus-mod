package woflo.petsplus.ai.context.perception;

@FunctionalInterface
public interface PerceptionListener {
    void onStimulus(PerceptionStimulus stimulus);
}
