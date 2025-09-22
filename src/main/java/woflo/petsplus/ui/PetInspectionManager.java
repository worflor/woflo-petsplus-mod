package woflo.petsplus.ui;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.state.PetComponent;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.util.Formatting;

import java.util.*;

/**
 * Shows pet context in boss bar when a player looks at their pet.
 * Rotates between lines to keep info readable.
 */
public final class PetInspectionManager {
    private PetInspectionManager() {}

    private static final int VIEW_DIST = 12;
    private static final Map<UUID, InspectionState> inspecting = new HashMap<>();
    private static final int LINGER_TICKS = 20; // ~1s linger after looking away

    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            updateForPlayer(player);
        }
    }

    private static void updateForPlayer(ServerPlayerEntity player) {
        MobEntity pet = findLookedAtPet(player);
        UUID pid = player.getUuid();

        InspectionState st = inspecting.computeIfAbsent(pid, k -> new InspectionState());
        if (pet == null) {
            // Allow linger before removing
            if (st.lingerTicks == LINGER_TICKS) {
                // Just stopped looking: extend current bar duration to linger window
                BossBarManager.extendDuration(player, LINGER_TICKS);
            }
            if (st.lingerTicks > 0) {
                st.lingerTicks--;
            } else {
                BossBarManager.removeBossBar(player);
                inspecting.remove(pid);
            }
            return;
        } else {
            // New/continued look: reset linger and pet reference
            st.lingerTicks = LINGER_TICKS;
            UUID newId = pet.getUuid();
            if (st.lastPetId == null || !st.lastPetId.equals(newId)) {
                // Swapped pets: reset rotation and XP preference
                st.tickCounter = 0;
                st.lastXp = -1f;
                st.lastXpChangeTick = 0;
                st.lastPetId = newId;
            }
        }

        PetComponent comp = PetComponent.get(pet);
        if (comp == null || !comp.isOwnedBy(player)) {
            // Only show for owned pets
            BossBarManager.removeBossBar(player);
            inspecting.remove(pid);
            return;
        }

        st.tick();

        Text nameLevel = buildNameLevelText(pet, comp);

        List<Frame> frames = new ArrayList<>();

        // Priorities: 1) recent XP change, 2) HP if injured, 3) CD/Aura, else default to XP
        boolean showXp = st.shouldShowXp(comp);
        boolean showHp = false;
        float health = pet.getHealth();
        float max = Math.max(1f, pet.getMaxHealth());
        if (health < max) {
            showHp = true;
        }

        // CD/Aura frame if applicable
        List<Text> cdAura = buildCdAuraLines(comp);
        boolean hasCdAura = !cdAura.isEmpty();
        if (hasCdAura) {
            int subIdx = st.subIndex(cdAura.size());
            Text extra = cdAura.get(subIdx);
            // Append to name-level for context
            Text text = nameLevel.copy()
                .append(UIStyle.sepDot())
                .append(extra);
            // Color: aura lines should be GREEN; cooldown lines YELLOW
            String s = extra.getString().toLowerCase(java.util.Locale.ROOT);
            BossBar.Color color = s.contains("aura:") ? BossBar.Color.GREEN : BossBar.Color.YELLOW;
            // Aura should be fixed-present (full) bar, not a countdown
            frames.add(new Frame(text, 1.0f, color));
        }

        // Decide which primary frame comes first
        if (showXp) {
            frames.add(0, new Frame(nameLevel, comp.getXpProgress(), BossBar.Color.GREEN));
        } else if (showHp) {
            frames.add(0, new Frame(nameLevel, Math.max(0f, health / max), BossBar.Color.RED));
        } else if (!hasCdAura) {
            // No signal? Default to XP quietly
            frames.add(0, new Frame(nameLevel, comp.getXpProgress(), BossBar.Color.GREEN));
        }

        if (frames.isEmpty()) return;

        // Rotate frames every 40 ticks
        int idx = st.frameIndex(frames.size());
        Frame current = frames.get(idx);

        // Show/update boss bar with fixed percent for XP/HP, info bar for CD/Aura
        boolean isInfo = current.color == BossBar.Color.YELLOW; // only cooldowns count down
        if (isInfo) {
            BossBarManager.showOrUpdateInfoBar(player, current.text, current.color, 12);
        } else {
            BossBarManager.showOrUpdateBossBar(player, current.text, current.percent, current.color, 12);
        }
    }

    private static MobEntity findLookedAtPet(ServerPlayerEntity player) {
        Vec3d start = player.getCameraPosVec(1f);
        Vec3d look = player.getRotationVec(1f);
    // Ray end not strictly needed; we use angular alignment with nearby entities for robustness

        // Entity raycast alternative: iterate nearby entities and pick best alignment
        double bestDot = 0.98; // require tight alignment
        MobEntity best = null;
        for (Entity e : player.getWorld().getOtherEntities(player, player.getBoundingBox().expand(VIEW_DIST))) {
            if (!(e instanceof MobEntity mob)) continue;
            Vec3d to = e.getPos().add(0, e.getStandingEyeHeight()*0.5, 0).subtract(start).normalize();
            double dot = to.dotProduct(look);
            if (dot > bestDot && player.squaredDistanceTo(e) <= VIEW_DIST*VIEW_DIST) {
                bestDot = dot;
                best = mob;
            }
        }
        return best;
    }

    private static class InspectionState {
        int tickCounter = 0;
        int lingerTicks = 0;
        UUID lastPetId = null;
        float lastXp = -1f;
        long lastXpChangeTick = 0;

        void tick() { tickCounter++; }

        int frameIndex(int frames) {
            if (frames <= 0) return 0;
            return (tickCounter / 40) % frames; // rotate every 2 seconds
        }

        int subIndex(int size) {
            if (size <= 0) return 0;
            return (tickCounter / 40) % size;
        }

        boolean shouldShowXp(PetComponent comp) {
            float xp = comp.getXpProgress();
            long now = comp.getPet().getWorld() != null ? comp.getPet().getWorld().getTime() : 0L;
            boolean levelUp = comp.isFeatureLevel(); // simple proxy; optional
            if (lastXp < 0f) {
                lastXp = xp;
                lastXpChangeTick = now;
                return true; // on first sight, show XP once
            }
            if (Math.abs(xp - lastXp) > 0.001f || levelUp) {
                lastXp = xp;
                lastXpChangeTick = now;
                return true;
            }
            // Prefer XP for ~5 seconds after change
            return (now - lastXpChangeTick) <= 100;
        }
    }

    private record Frame(Text text, float percent, BossBar.Color color) {}

    private static Text buildNameLevelText(MobEntity pet, PetComponent comp) {
        String name = pet.getDisplayName().getString();
        int level = comp.getLevel();
        return UIStyle.bold(Text.literal(name).formatted(Formatting.AQUA))
            .append(UIStyle.secondary(" - "))
            .append(UIStyle.secondary("Lv "))
            .append(UIStyle.value(String.valueOf(level), Formatting.GREEN));
    }

    @SuppressWarnings("unchecked")
    private static List<Text> buildCdAuraLines(PetComponent comp) {
        List<Text> out = new ArrayList<>();
        // Aura (support) summary
        List<String> effects = comp.getStateData("support_potion_effects", List.class);
        // Integer auraDuration = comp.getStateData("support_potion_aura_duration", Integer.class);
        Boolean hasPotion = comp.getStateData("support_potion_present", Boolean.class);
        if (Boolean.TRUE.equals(hasPotion) && effects != null && !effects.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int shown = 0;
            for (String s : effects) {
                String[] parts = s.split("\\|");
                String eff = parts.length > 0 ? parts[0] : s;
                // Format amplifier: hide 0, show roman numerals starting at II for amp>=1
                String amp = "";
                if (parts.length > 1) {
                    try {
                        int a = Integer.parseInt(parts[1]);
                        if (a >= 1) amp = " " + toRoman(a + 1);
                    } catch (NumberFormatException ignored) {}
                }
                if (shown > 0) sb.append(", ");
                sb.append(shortenId(eff)).append(amp);
                if (++shown >= 3) break;
            }
            Text t = UIStyle.secondary("Aura: ")
                .append(UIStyle.value(sb.toString(), Formatting.LIGHT_PURPLE));
            out.add(t);
        }

        // Cooldowns (up to 3)
        String[] knownCds = new String[]{
            "ability_primary_cd", "ability_secondary_cd", "guardian_bulwark_cd",
            "striker_exec_cd", "scout_recon_cd", "skyrider_winds_cd", "eclipsed_blink_cd"
        };
        int cdShown = 0;
        for (String key : knownCds) {
            long remain = comp.getRemainingCooldown(key);
            if (remain > 0) {
                Text t = UIStyle.secondary("CD: ")
                    .append(UIStyle.primary(humanizeKey(key)))
                    .append(UIStyle.secondary(" "))
                    .append(UIStyle.value(formatTicks(remain), Formatting.YELLOW));
                out.add(t);
                if (++cdShown >= 3) break;
            }
        }

        return out;
    }

    private static String shortenId(String id) {
        int idx = id.indexOf(':');
        if (idx > 0 && idx < id.length()-1) {
            return id.substring(idx+1).replace('_',' ');
        }
        return id.replace('_',' ');
    }

    private static String toRoman(int n) {
        // 1..10 range is enough for potion amps; clamp for safety
        int v = Math.max(1, Math.min(10, n));
        return switch (v) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            default -> "X";
        };
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
