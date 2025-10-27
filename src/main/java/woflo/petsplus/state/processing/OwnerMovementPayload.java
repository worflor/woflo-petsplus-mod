package woflo.petsplus.state.processing;

import org.jetbrains.annotations.Nullable;

/**
 * Composite payload shared with movement owner events so perception bridges
 * can derive both spatial arrangements and owner focus information without
 * scheduling additional work.
 */
public record OwnerMovementPayload(@Nullable OwnerSpatialResult spatialResult,
                                   @Nullable OwnerFocusSnapshot focusSnapshot) {

    public OwnerMovementPayload withSpatialResult(@Nullable OwnerSpatialResult spatial) {
        if (spatial == spatialResult) {
            return this;
        }
        return new OwnerMovementPayload(spatial, focusSnapshot);
    }

    public OwnerMovementPayload withFocusSnapshot(@Nullable OwnerFocusSnapshot snapshot) {
        if (snapshot == focusSnapshot) {
            return this;
        }
        return new OwnerMovementPayload(spatialResult, snapshot);
    }
}

