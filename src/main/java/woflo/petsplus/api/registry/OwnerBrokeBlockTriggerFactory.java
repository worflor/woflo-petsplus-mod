package woflo.petsplus.api.registry;

import net.minecraft.util.Identifier;
import woflo.petsplus.api.Trigger;
import woflo.petsplus.api.TriggerContext;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

final class OwnerBrokeBlockTriggerFactory {

    private OwnerBrokeBlockTriggerFactory() {
    }

    static Trigger create(Identifier triggerId, Optional<Boolean> requireValuable,
                          Optional<Identifier> requiredBlockId, int cooldown) {
        Objects.requireNonNull(triggerId, "triggerId");
        Objects.requireNonNull(requireValuable, "requireValuable");
        Objects.requireNonNull(requiredBlockId, "requiredBlockId");
        return new Trigger() {
            @Override
            public Identifier getId() {
                return triggerId;
            }

            @Override
            public int getInternalCooldownTicks() {
                return cooldown;
            }

            @Override
            public boolean shouldActivate(TriggerContext context) {
                String eventType = context.getEventType();
                String expectedEvent = triggerId.getPath();
                if (!Objects.equals(eventType, expectedEvent)
                    && !Objects.equals(eventType, triggerId.toString())) {
                    return false;
                }
                if (requiredBlockId.isPresent()
                    && !matchesRequiredBlock(context, requiredBlockId.get())) {
                    return false;
                }
                if (requireValuable.isEmpty()) {
                    return true;
                }
                Boolean isValuable = context.getData("block_valuable", Boolean.class);
                if (isValuable == null) {
                    return false;
                }
                return isValuable.equals(requireValuable.get());
            }
        };
    }

    private static boolean matchesRequiredBlock(TriggerContext context, Identifier expected) {
        Identifier payloadIdentifier = context.getData("block_identifier", Identifier.class);
        if (identifiersEqualIgnoreCase(payloadIdentifier, expected)) {
            return true;
        }

        String idString = context.getData("block_id", String.class);
        if (stringMatchesIdentifier(idString, expected)) {
            return true;
        }

        String noNamespace = context.getData("block_id_no_namespace", String.class);
        return noNamespace != null && noNamespace.equalsIgnoreCase(expected.getPath());
    }

    private static boolean identifiersEqualIgnoreCase(@Nullable Identifier actual, Identifier expected) {
        if (actual == null) {
            return false;
        }
        if (actual.equals(expected)) {
            return true;
        }
        return actual.getNamespace().equalsIgnoreCase(expected.getNamespace())
            && actual.getPath().equalsIgnoreCase(expected.getPath());
    }

    private static boolean stringMatchesIdentifier(@Nullable String candidate, Identifier expected) {
        if (candidate == null || candidate.isEmpty()) {
            return false;
        }
        if (candidate.equalsIgnoreCase(expected.toString())) {
            return true;
        }
        Identifier parsed = Identifier.tryParse(candidate.toLowerCase(Locale.ROOT));
        if (identifiersEqualIgnoreCase(parsed, expected)) {
            return true;
        }
        return candidate.equalsIgnoreCase(expected.getPath());
    }
}
