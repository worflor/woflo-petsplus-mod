// ... existing code ...
package woflo.petsplus.util;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * Centralized, public-APIs-only melee reach utility.
 * Intent: avoid protected Minecraft calls (e.g., MobEntity.isInAttackRange)
 * to remain safe for plain JVM tests and reduce VerifyError risks.
 */
public final class CombatReachUtil {

    private CombatReachUtil() {}

    /**
     * Checks if target is within melee attack range of attacker using only public APIs.
     * Guards nulls, removal, and world mismatches.
     */
    public static boolean isInAttackRange(MobEntity attacker, LivingEntity target) {
        if (attacker == null || target == null) return false;
        if (!attacker.isAlive() || attacker.isRemoved()) return false;
        if (!target.isAlive() || target.isRemoved()) return false;
        if (attacker.getEntityWorld() == null || target.getEntityWorld() == null) return false;
        if (attacker.getEntityWorld() != target.getEntityWorld()) return false;

        double distSq = attacker.squaredDistanceTo(target);
        double reach = computeMeleeReach(attacker, target);
        return distSq <= (reach * reach);
    }

    /**
     * Computes the effective melee reach radius used by isInAttackRange.
     * Uses only public APIs and guarded reflection for optional attributes.
     */
    public static double computeMeleeReach(MobEntity attacker, LivingEntity target) {
        if (attacker == null || target == null) return 0.0;
        if (!attacker.isAlive() || attacker.isRemoved()) return 0.0;
        if (!target.isAlive() || target.isRemoved()) return 0.0;
        if (attacker.getEntityWorld() == null || target.getEntityWorld() == null) return 0.0;
        if (attacker.getEntityWorld() != target.getEntityWorld()) return 0.0;

        // Base reach with entity widths, clamped
        double baseReach = 2.0
            + Math.max(0.75 * attacker.getWidth(), 0.5)
            + 0.25 * Math.max(target.getWidth(), 0.4);
        baseReach = clamp(baseReach, 1.5, 4.5);

        // Try optional attribute via reflection without static linkage
        double attrReach = readOptionalReachAttribute(attacker);
        if (attrReach > 0.0) {
            baseReach = Math.max(baseReach, attrReach);
        }

        return baseReach;
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    /**
     * Attempts to read a "reach" style attribute without linking to non-existent constants across versions.
     * Strategy:
     * - Reflectively access net.minecraft.entity.attribute.EntityAttributes
     * - Probe fields commonly named ATTACK_RANGE or REACH (case-insensitive fallbacks)
     * - If found, resolve the registry entry from attacker attributes and fetch its current value
     * - Swallow all errors and return 0.0 on failure
     */
    private static double readOptionalReachAttribute(MobEntity attacker) {
        try {
            Class<?> clazz = EntityAttributes.class;
            java.lang.reflect.Field reachField = null;

            // Try canonical names first
            try {
                reachField = clazz.getField("ATTACK_RANGE");
            } catch (NoSuchFieldException ignored) {}
            if (reachField == null) {
                try {
                    reachField = clazz.getField("REACH");
                } catch (NoSuchFieldException ignored) {}
            }

            // Fall back to scanning public fields for likely names
            if (reachField == null) {
                for (java.lang.reflect.Field f : clazz.getFields()) {
                    String name = f.getName();
                    if (name == null) continue;
                    String n = name.toUpperCase(java.util.Locale.ROOT);
                    if (n.contains("REACH") || n.contains("ATTACK_RANGE")) {
                        reachField = f;
                        break;
                    }
                }
            }

            if (reachField == null) {
                return 0.0;
            }

            Object attrRef = reachField.get(null);
            if (attrRef == null) {
                return 0.0;
            }

            // Path A: already a RegistryEntry<EntityAttribute>
            if (attrRef instanceof RegistryEntry<?> entryRaw) {
                Object maybeAttr = entryRaw.value();
                if (maybeAttr instanceof EntityAttribute) {
                    @SuppressWarnings("unchecked")
                    RegistryEntry<EntityAttribute> cast = (RegistryEntry<EntityAttribute>) entryRaw;
                    double value = attacker.getAttributeValue(cast);
                    return value > 0.0 ? value : 0.0;
                }
            }

            // Path B: raw EntityAttribute -> try resolve to registry entry
            if (attrRef instanceof EntityAttribute ea) {
                RegistryEntry<EntityAttribute> entry = Registries.ATTRIBUTE.getEntry(ea);
                if (entry != null) {
                    double value = attacker.getAttributeValue(entry);
                    return value > 0.0 ? value : 0.0;
                }
            }

            // Path C: reflective "value()" method returning EntityAttribute or RegistryEntry<EntityAttribute>
            try {
                java.lang.reflect.Method valueMethod = attrRef.getClass().getMethod("value");
                Object inner = valueMethod.invoke(attrRef);
                if (inner instanceof EntityAttribute ea2) {
                    RegistryEntry<EntityAttribute> entry = Registries.ATTRIBUTE.getEntry(ea2);
                    if (entry != null) {
                        double value = attacker.getAttributeValue(entry);
                        return value > 0.0 ? value : 0.0;
                    }
                } else if (inner instanceof RegistryEntry<?> innerEntryRaw) {
                    Object maybeAttr2 = innerEntryRaw.value();
                    if (maybeAttr2 instanceof EntityAttribute) {
                        @SuppressWarnings("unchecked")
                        RegistryEntry<EntityAttribute> cast = (RegistryEntry<EntityAttribute>) innerEntryRaw;
                        double value = attacker.getAttributeValue(cast);
                        return value > 0.0 ? value : 0.0;
                    }
                }
            } catch (Throwable ignored) {
                // Try "get()" as alternate accessor
                try {
                    java.lang.reflect.Method getMethod = attrRef.getClass().getMethod("get");
                    Object inner2 = getMethod.invoke(attrRef);
                    if (inner2 instanceof EntityAttribute ea3) {
                        RegistryEntry<EntityAttribute> entry = Registries.ATTRIBUTE.getEntry(ea3);
                        if (entry != null) {
                            double value = attacker.getAttributeValue(entry);
                            return value > 0.0 ? value : 0.0;
                        }
                    } else if (inner2 instanceof RegistryEntry<?> innerEntryRaw2) {
                        Object maybeAttr3 = innerEntryRaw2.value();
                        if (maybeAttr3 instanceof EntityAttribute) {
                            @SuppressWarnings("unchecked")
                            RegistryEntry<EntityAttribute> cast = (RegistryEntry<EntityAttribute>) innerEntryRaw2;
                            double value = attacker.getAttributeValue(cast);
                            return value > 0.0 ? value : 0.0;
                        }
                    }
                } catch (Throwable ignored2) {
                    // give up
                }
            }

            return 0.0;
        } catch (Throwable t) {
            // Swallow all errors to remain robust across environments
            return 0.0;
        }
    }
}
// ... existing code ...