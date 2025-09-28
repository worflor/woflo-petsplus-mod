package woflo.petsplus.state.processing;

@FunctionalInterface
public interface OwnerEventListener {
    void onOwnerEvent(OwnerEventFrame frame);
}
