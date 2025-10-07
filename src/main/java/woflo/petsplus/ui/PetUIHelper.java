package woflo.petsplus.ui;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.api.registry.PetsPlusRegistries;
import woflo.petsplus.state.PetComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds user-friendly UI lines for a pet, avoiding hardcoding by reading PetComponent state.
 */
public final class PetUIHelper {
    private PetUIHelper() {}

    public static List<Text> buildInspectionLines(MobEntity pet, PetComponent comp) {
        List<Text> lines = new ArrayList<>();

        // Line 1: Name • Role • Level
        String name = pet.getDisplayName().getString();
        Identifier roleId = comp.getRoleId();
        PetRoleType roleType = PetsPlusRegistries.petRoleTypeRegistry().get(roleId);
        int level = comp.getLevel();
        String roleName;
        if (roleType != null) {
            String translated = Text.translatable(roleType.translationKey()).getString();
            // Use translation if available, otherwise format the ID path
            if (!translated.equals(roleType.translationKey())) {
                roleName = translated;
            } else {
                // Fallback: format ID path as "Title Case"
                String path = roleId.getPath();
                String[] parts = path.split("_");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) {
                    if (!part.isEmpty()) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(Character.toUpperCase(part.charAt(0)));
                        if (part.length() > 1) sb.append(part.substring(1));
                    }
                }
                roleName = sb.toString();
            }
        } else {
            // Format ID path as "Title Case"
            String path = roleId.getPath();
            String[] parts = path.split("_");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) sb.append(part.substring(1));
                }
            }
            roleName = sb.toString();
        }

        Text line1 = UIStyle.bold(Text.literal(name).formatted(Formatting.AQUA))
            .append(UIStyle.sepDot())
            .append(UIStyle.italic(Text.literal(roleName).formatted(Formatting.GOLD)))
            .append(UIStyle.sepDot())
            .append(UIStyle.secondary("Lv "))
            .append(UIStyle.value(String.valueOf(level), Formatting.GREEN));

        // Line 2: Health and XP progress
        float health = pet.getHealth();
        float maxHealth = pet.getMaxHealth();
        int healthPct = Math.round((health / maxHealth) * 100);
        String hp = String.format("%.0f/%.0f (%d%%)", health, maxHealth, healthPct);
        int xpPct = Math.round(comp.getXpProgress() * 100);

        Text line2 = UIStyle.secondary("HP: ")
            .append(UIStyle.value(hp, Formatting.RED))
            .append(UIStyle.spacer())
            .append(UIStyle.secondary("XP: "))
            .append(UIStyle.value(xpPct + "%", Formatting.YELLOW));

        lines.add(line1);
        lines.add(line2);

        // Screen 2: Summarized cooldowns and optional potion aura
        addCondensedCooldownsAndAura(lines, comp);

        return lines;
    }

    @SuppressWarnings("unchecked")
    private static void addCondensedCooldownsAndAura(List<Text> lines, PetComponent comp) {
        // Build a compact cooldown summary: CD: name time, name time, name time
        String[] knownCds = new String[]{
            "ability_primary_cd", "ability_secondary_cd", "guardian_bulwark_cd",
            "striker_exec_cd", "scout_recon_cd", "skyrider_winds_cd", "eclipsed_blink_cd"
        };
        StringBuilder cdSb = new StringBuilder();
        int cdShown = 0;
        for (String key : knownCds) {
            long remain = comp.getRemainingCooldown(key);
            if (remain > 0) {
                if (cdShown > 0) cdSb.append(", ");
                cdSb.append(humanizeKey(key)).append(" ").append(formatTicks(remain));
                cdShown++;
                if (cdShown >= 3) break;
            }
        }

        // Optional: potion aura summary
        List<String> effects = comp.getStateData("support_potion_effects", List.class);
        Integer auraDuration = comp.getStateData("support_potion_aura_duration", Integer.class);
        Boolean hasPotion = comp.getStateData("support_potion_present", Boolean.class);
        Double chargesRemaining = comp.getStateData("support_potion_charges_remaining", Double.class);
        Double totalCharges = comp.getStateData("support_potion_total_charges", Double.class);
        Text line3 = null;

        if (cdShown > 0) {
            line3 = UIStyle.secondary("CD: ")
                .append(UIStyle.value(cdSb.toString(), Formatting.YELLOW));
        }

        if (Boolean.TRUE.equals(hasPotion) && effects != null && !effects.isEmpty()) {
            // Build a short effects list (up to 2 entries)
            StringBuilder effSb = new StringBuilder();
            int shown = 0;
            for (String s : effects) {
                String[] parts = s.split("\\|");
                String eff = parts.length > 0 ? parts[0] : s;
                String amp = parts.length > 1 ? "+" + parts[1] : "";
                if (shown > 0) effSb.append(", ");
                effSb.append(shortenId(eff)).append(amp);
                shown++;
                if (shown >= 2) break;
            }
            String dur = auraDuration != null ? (auraDuration / 20) + "s" : "";
            Text auraText = UIStyle.secondary(line3 == null ? "Aura: " : " • Aura: ")
                .append(UIStyle.value(effSb.toString(), Formatting.LIGHT_PURPLE))
                .append(dur.isEmpty() ? Text.empty() : UIStyle.secondary(" (" + dur + ")"));
            if (chargesRemaining != null) {
                int pulsesLeft = Math.max(0, (int) Math.ceil(chargesRemaining));
                String chargeText;
                if (totalCharges != null && totalCharges > 0) {
                    int pulsesTotal = Math.max(0, (int) Math.round(totalCharges));
                    chargeText = pulsesLeft + "/" + pulsesTotal;
                } else {
                    chargeText = String.valueOf(pulsesLeft);
                }
                auraText = auraText.copy().append(UIStyle.secondary(" [" + chargeText + "]"));
            }
            line3 = (line3 == null) ? auraText : line3.copy().append(auraText);
        }

        if (line3 != null) {
            lines.add(line3);
        }
    }

    private static String shortenId(String id) {
        int idx = id.lastIndexOf(':');
        if (idx > 0 && idx < id.length()-1) {
            return id.substring(idx+1).replace('_',' ');
        }
        return id.replace('_',' ');
    }

    private static String humanizeKey(String key) {
        return key.replace('_', ' ');
    }

    private static String formatTicks(long ticks) {
        long sec = Math.max(0, ticks) / 20;
        long m = sec / 60;
        long s = sec % 60;
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}