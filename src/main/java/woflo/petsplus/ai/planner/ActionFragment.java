package woflo.petsplus.ai.planner;

import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Describes a reusable fragment of behaviour that can be composed into an action plan.
 */
public record ActionFragment(
    Identifier id,
    String description,
    List<String> tags
) {
    public ActionFragment {
        if (id == null) {
            throw new IllegalArgumentException("id");
        }
        description = description == null ? "" : description;
        tags = tags == null || tags.isEmpty() ? List.of() : List.copyOf(tags);
    }
}

