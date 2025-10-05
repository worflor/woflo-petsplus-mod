package woflo.petsplus.state.modules;

public interface DataBackedModule<D> extends PetModule {
    D toData();
    void fromData(D data);
}
