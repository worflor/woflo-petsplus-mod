package woflo.petsplus.stats.nature.harmony;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable description of a harmony or disharmony combination authored via datapack.
 */
public final class NatureHarmonySet {

    public enum Type {
        HARMONY,
        DISHARMONY
    }

    private final Identifier id;
    private final Type type;
    private final List<Member> members;
    private final Set<Identifier> memberNatures;
    private final double radius;
    private final float moodScalar;
    private final float contagionScalar;
    private final float volatilityScalar;
    private final float resilienceScalar;
    private final float guardScalar;
    private final int lingerTicks;
    private final List<String> tags;
    private final String canonicalKey;

    public NatureHarmonySet(Identifier id,
                             Type type,
                             List<Member> members,
                             double radius,
                             float moodScalar,
                             float contagionScalar,
                             float volatilityScalar,
                             float resilienceScalar,
                             float guardScalar,
                             int lingerTicks,
                             List<String> tags) {
        if (id == null) {
            throw new IllegalArgumentException("Harmony set id cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Harmony set type cannot be null");
        }
        List<Member> normalizedMembers = sanitizeMembers(members);
        if (normalizedMembers.size() < 2 || normalizedMembers.size() > 3) {
            throw new IllegalArgumentException("Harmony sets must contain 2 or 3 member natures");
        }
        this.id = id;
        this.type = type;
        this.members = List.copyOf(normalizedMembers);
        Set<Identifier> natureSet = new LinkedHashSet<>();
        for (Member member : this.members) {
            natureSet.add(member.natureId());
        }
        this.memberNatures = Set.copyOf(natureSet);
        this.radius = Math.max(0.1d, radius);
        this.moodScalar = sanitizeScalar(moodScalar);
        this.contagionScalar = sanitizeScalar(contagionScalar);
        this.volatilityScalar = sanitizeScalar(volatilityScalar);
        this.resilienceScalar = sanitizeScalar(resilienceScalar);
        this.guardScalar = sanitizeScalar(guardScalar);
        this.lingerTicks = Math.max(0, lingerTicks);
        this.tags = tags == null || tags.isEmpty() ? List.of() : List.copyOf(tags);
        this.canonicalKey = buildCanonicalKey(this.members);
    }

    private static float sanitizeScalar(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 1.0f;
        }
        return MathHelper.clamp(value, 0.05f, 4.0f);
    }

    private static List<Member> sanitizeMembers(List<Member> members) {
        if (members == null) {
            return List.of();
        }
        List<Member> cleaned = new ArrayList<>(members.size());
        for (Member member : members) {
            if (member != null) {
                cleaned.add(member);
            }
        }
        cleaned.sort((a, b) -> a.canonicalValue().compareToIgnoreCase(b.canonicalValue()));
        return cleaned;
    }

    private static String buildCanonicalKey(List<Member> members) {
        if (members.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>(members.size());
        for (Member member : members) {
            names.add(member.canonicalValue());
        }
        Collections.sort(names);
        return String.join("|", names);
    }

    public Identifier id() {
        return id;
    }

    public Type type() {
        return type;
    }

    public List<Member> members() {
        return members;
    }

    public boolean contains(Identifier natureId) {
        return natureId != null && memberNatures.contains(natureId);
    }

    public double radius() {
        return radius;
    }

    public float moodScalar() {
        return moodScalar;
    }

    public float contagionScalar() {
        return contagionScalar;
    }

    public float volatilityScalar() {
        return volatilityScalar;
    }

    public float resilienceScalar() {
        return resilienceScalar;
    }

    public float guardScalar() {
        return guardScalar;
    }

    public int lingerTicks() {
        return lingerTicks;
    }

    public List<String> tags() {
        return tags;
    }

    public String canonicalKey() {
        return canonicalKey;
    }

    @Override
    public String toString() {
        return "NatureHarmonySet{" +
            "id=" + id +
            ", type=" + type +
            ", members=" + members +
            ", radius=" + radius +
            ", moodScalar=" + moodScalar +
            ", contagionScalar=" + contagionScalar +
            ", volatilityScalar=" + volatilityScalar +
            ", resilienceScalar=" + resilienceScalar +
            ", guardScalar=" + guardScalar +
            ", lingerTicks=" + lingerTicks +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NatureHarmonySet that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
}

    /**
     * Describes one nature (optionally constrained by specific astrology signs) that participates in a harmony set.
     */
    public static final class Member {

        private final Identifier natureId;
        private final Set<Identifier> allowedSigns;
        private final String canonicalValue;

        public Member(Identifier natureId, Set<Identifier> allowedSigns) {
            if (natureId == null) {
                throw new IllegalArgumentException("Member nature id cannot be null");
            }
            this.natureId = natureId;
            if (allowedSigns == null || allowedSigns.isEmpty()) {
                this.allowedSigns = Set.of();
            } else {
                Set<Identifier> cleaned = new LinkedHashSet<>(allowedSigns.size());
                for (Identifier sign : allowedSigns) {
                    if (sign != null) {
                        cleaned.add(sign);
                    }
                }
                this.allowedSigns = cleaned.isEmpty() ? Set.of() : Set.copyOf(cleaned);
            }
            this.canonicalValue = buildCanonicalValue(this.natureId, this.allowedSigns);
        }

        private static String buildCanonicalValue(Identifier natureId, Set<Identifier> allowedSigns) {
            String nature = natureId.toString().toLowerCase(Locale.ROOT);
            if (allowedSigns.isEmpty()) {
                return nature;
            }
            List<String> signs = new ArrayList<>(allowedSigns.size());
            for (Identifier sign : allowedSigns) {
                signs.add(sign.toString().toLowerCase(Locale.ROOT));
            }
            Collections.sort(signs);
            return nature + "#" + String.join("&", signs);
        }

        public Identifier natureId() {
            return natureId;
        }

        public Set<Identifier> allowedSigns() {
            return allowedSigns;
        }

        public boolean matches(Identifier nature, Identifier sign) {
            if (!natureId.equals(nature)) {
                return false;
            }
            if (allowedSigns.isEmpty()) {
                return true;
            }
            return sign != null && allowedSigns.contains(sign);
        }

        private String canonicalValue() {
            return canonicalValue;
        }

        @Override
        public String toString() {
            return "Member{" +
                "natureId=" + natureId +
                ", allowedSigns=" + allowedSigns +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Member member)) return false;
            return Objects.equals(natureId, member.natureId) && Objects.equals(allowedSigns, member.allowedSigns);
        }

        @Override
        public int hashCode() {
            return Objects.hash(natureId, allowedSigns);
        }
    }
}
