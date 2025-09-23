# Pets+ (Pets Plus)
A Minecraft mod that expands pets with unique roles and abilities to add new twists on vanilla mechanics.

## Pet Roles and Levelling
Each role has a unique set of abilities, and can be applied to *any* tamable/trustable entity. *So MaNY wAcKy CoMbInAtiOnS*!

1. **Guardian** — a loyal shield that steps between you and the world. It absorbs punishment for you and steadies you against knockbacks and volleys.

2. **Striker** — a closer with a taste for last hits. It marks faltering foes and powers up your next hit to finish the job.

3. **Support** — a quiet caretaker that keeps you going longer. It makes your sips and buffs go further and spreads helpful auras ahead.

4. **Scout** — eyes like lanterns, pockets for days. It lights up the first threat and coaxes nearby loot and XP your way.

5. **Skyrider** — wind at your back, sky in your stride. It softens falls and can pop enemies into the air with your strikes.

6. **Enchantment-Bound** — your gear’s echo, humming with second chances. It extends work-speed surges and nudges drop luck in your favor.

7. **Cursed One** — a beautiful bad omen that turns fear into leverage. When you’re hurting, it rattles nearby foes and lets a payback swing restore you.

8. **Eepy Eeper** — cozy magic that rewards slowing down. Let it rest and a quiet healing bubble blooms around you.

9. **Eclipsed** — void-kissed mischief that paints targets for ruin. It brands enemies, gives sudden speed bursts, and sends out pings that reveal nearby lurkers.

Your pet shares your experience and grows alongside you. Feature levels (3, 7, 12, 17, 23, 27) unlock new abilities and grant XP bonuses. At tribute gates, offer a token to continue growing:
- **Level 10 →** Gold Ingot  
- **Level 20 →** Diamond  
- **Level 30 →** Netherite Ingot

## Pet Death and Loss
If your pet dies, it will drop all its items and be gone for good. Your tribute items are lost and all pet experience is lost. You will need to invest in a new pet.

## Configuration and Expandability
Pets+ now keeps its settings under `config/petsplus/`. The first time you launch the mod it migrates any legacy `petsplus.json` into this layout (and stashes the old file alongside it for reference) so you can start editing without losing your tweaks.

- `core.json` — the shared hub for `abilities`, `petting`, `pet_leveling`, `tribute_items`, `visuals`, and any other global toggles. Tweak tribute gates, petting cooldowns, XP sharing, and visual presets here; if you leave a key out the game simply falls back to the datapack default at runtime.

- `roles/` — each registered role ID gets its own file (for example `config/petsplus/roles/petsplus/guardian.json`). Files are scaffolded from the datapack definition with tribute milestones, passive aura timing, and support potion defaults, and new datapack or add-on roles pick up the same hints automatically so you never start from an empty object.

- Add-ons can drop new JSON files under `roles/<namespace>/` to override datapack-supplied kits. Leaving a file out simply falls back to the datapack behavior, so you only touch the roles you care about.

- The `abilities` object inside `core.json` is where ability-specific tuning lives. Add overrides under the ability's full ID (for example `"petsplus:shield_bash_rider"`) and the runtime merges them with the datapack definition while leaving the file untouched for keys you don't set.

Want to push further? Datapacks can inject new roles under `data/<namespace>/roles/*.json` and new ability payloads under `data/<namespace>/abilities/*.json`, and Fabric mods can register fresh trigger/effect/role types that flow straight through these configs.

## Credits
- woflo - (me)
