<p align="center">
  <img src="src/main/resources/assets/petsplus/icon.png"
       alt="Pets+ icon"
       width="128">
</p>

<h1 align="center">
  Pets+ <small>dev-0.93-hashtag-mood-hashtag-documentation</small>
</h1>

A standalone Minecraft mod that expands tames and trusted mobs to add new twists on vanilla mechanics.

---
  **[Feature Documentation](docs/features/_readme.md)**
---


## Pet Roles, Levelling, and Natures
Each role has a unique set of abilities, and can be applied to *any* tamable/trustable entity. *So MaNY wAcKy CoMbInAtiOnS*!

1. **Guardian** — a loyal shield that steps between you and the world. It absorbs punishment for you and steadies you against knockbacks and volleys.
2. **Striker** — a closer with a taste for last hits. Empowering it's owner while dealing devestating blows to hurting hostiles.
3. **Support** — a cozy caretaker that keeps you going longer. It makes your brews and buffs last longer and shares helpful auras among allies.
4. **Scout** — eyes like lanterns, pockets for days. It lights up the first threat and coaxes nearby loot and XP your way.
5. **Skyrider** — wind at your back, sky in your stride. It softens falls and can pop enemies into the air with your strikes.
6. **Enchantment-Bound** — your gear’s echo, humming with second chances. It extends work-speed surges and nudges drop luck in your favor.
7. **Cursed One** — a beautiful bad omen that turns fear into leverage. Like a curse of vanishing, this pet is tied to your soul. Die, and it dies.
8. **Eepy Eeper** — a cozy buddy that rewards slowing down. Let it rest and a quiet healing bubble blooms around you.
9. **Eclipsed** — void-kissed mischief that paints targets for ruin. It brands enemies, pings nearby lurkers, and allows mobile ender chest access.

Your pet shares your experience and grows alongside you. Feature levels (3, 7, 12, 17, 23, 27) unlock new abilities and bonuses. At tribute gates, offer a token to continue growing:
- **Level 10 →** Gold Ingot
- **Level 20 →** Diamond  
- **Level 30 →** Netherite Scrap

Pets also come with distinct natures based on the conditions found when being tamed or born.
### Wild Natures
1. Frisky [ Colder climates ]
2. Feral [ Neutral climates ]
3. Fierce [ Hotter climates ]

Born pets do not roll for tamed natures; tamed pets do not role for born natures. (wow)
### Born Natures
4. Radiant [ Daytime - Open sky - No weather ]
5. Festival [ 3+ pets or players nearby ]
6. Infernal [ In the Nether ]
7. Otherworldly [ Any dimension; Besides Overworld and Nether ]
8. Hearth [ i forgor ]
9. Nocturne [ Nighttime - Full moon - Open sky ]
10. Tempest [ Rain or thunder weather ]
11. Solace [ Birthed in solitude ]
12. Echoed [ Deep Dark biome ]
13. Mycelial [ Mushroom biome ]
14. Gilded [ Valuable blocks present ]
15. Gloom [ i forgor ]
16. Verdant [ Lush conditions present ]
17. Summit [ Height ≥100 - Open sky ]
18. Tidal [ Fully submerged in water - Ocean biome ]
19. Molten [ Near fiery blocks ]
20. Frosty [ Snowy biome ]
21. Mire [ i forgor ]
22. Relic [ Near strongholds / Ruins ]
23. Unnatural [ Breeder owns neither parents ]



## Moods and Emotion
Unfortunately, your *new friend* has developed *new feelings* too.

Behind the scenes, 30 emotions influence your pet's overall mood. Various events and triggers cause these emotional weights to shift and combine, blending into your pet's current **mood state**.
### Core Moods
1. **Happy** [ joyful ]
2. **Playful** [ energetic fun ]
3. **Curious** [ exploring ]
4. **Bonded** [ attached ]
5. **Calm** [ peaceful ]
6. **Passionate** [ enthusiastic ]
7. **Yūgen** [ profound awareness ]
8. **Focused** [ concentrated ]
9. **Sisu** [ determined endurance ]
10. **Saudade** [ wistful longing ]
11. **Protective** [ defensive ]
12. **Restless** [ agitated ]
13. **Afraid** [ scared ]
14. **Angry** [ mad ]

### Ultra-Rare Moods [ unimplemented | systems are in place, moods are not 'calculated' ]
15. **Echoed Resonance** — whispers from the deep; surviving Warden encounters or extended Deep Dark exposure awakens heightened senses and courage.
16. **Arcane Overflow** — drunk on enchantment power; proximity to heavy enchanting or high-level Enchantment-Bound pets floods the pet with arcane energy.
17. **Pack Spirit** — strength in numbers, unity in purpose; when 3+ bonded pets triumph together, the pack enters a synchronized state of shared power.

Mood intensity varies based on the strength of these emotional weights.
## Core Emotion Groups
- afflicted / positive
- threat / aversion
- reflective / neutral
- longing / resilient / aspiration

Watch, in real time, as beating your pet make them upset - or happy if they're into it.

I also heard a rumour pets take note of... **gossip**... for later use..



## New Friends
The following entities are now tamable:
1. Frogs with slimeballs
2. Rabbits with carrots
3. Turtles with seagrass

### Pet Trading!
Make your unwanted regrets someone else's problem! Sneak right clicking another player while your pet is on a leash, transfers the leash to the other player; trading ownership in the process. The pet will remember this...

### being considered
- Phantoms via Trust*
- tiny slimes and tiny magma cubes
- Camels
- Llamas
- Fully taming foxes
- Every base passive mob via Trust* [ add emotions to all passive mobs ]

### Unimplimented 
- Sniffers via a special 'Courting' system/mini-game


## Pet Life and Loss
If your pet dies, it will drop all its items and be gone forever. Your tribute items and all that pet's experience are lost. If only those memories weren't gone forever...

Hey wait, memorialize that trauma! A **Proof of Existence** drops on every pet death.

These keepsakes aren’t just tear-stained notes; they catalog the pet’s life, unlocked milestones, trait highlights, all until the moment they slipped away. Stack them in a memorial chest, read back the legends, and keep the pack’s stories breathing.

### unimplemented yet
1. Nuzlocke / Hardcore mode - Each player can only tame one of each entity type per save. Or, locked to one per biome.
2. Peaceful / Easy mode - Pets enter a 'downed' state before dying, allowing the player time to reverse a mistake.



## Advancements
The mod includes advancements across 5 categories: Bonding Basics, Emotional Journey, Mystical Connections, Role Specialization, and Special milestones.

**[View full advancement list →](docs/features/advancements.md)**



## Configuration and Expandability
Pets+ includes configuration files (`config/petsplus/...`) where you can customize various components. Explore it yourself.
- Roles, Abilities, Tributes, and Moods have configurations availible.
- The backend API is extendable via Datapacks and Add-On Mods. Base roles, abilities, progression rewards, and emotional events can be tinkered, modified, or overwritten to your liking.

## Performance
All compute heavy tasks are done asynchronously using background threads.



## Credits
- woflo - (me)
- woflo - (myself)
- woflo - (i)
- GuriCreates [ https://guricreates.com ] - Mod Icon
