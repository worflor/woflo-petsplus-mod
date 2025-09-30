package woflo.petsplus.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.advancement.AdvancementRequirements;
import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.advancement.AdvancementCriteriaRegistry;
import woflo.petsplus.advancement.criteria.*;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Data generator for PetsPlus advancements following Fabric 1.21.8 conventions.
 * Based on https://docs.fabricmc.net/develop/data-generation/advancements
 * 
 * Advancement Tree Structure:
 * - Root: first_pet (bonding with first pet)
 *   ├─ Leveling Chain: trial_ready → even_bester → or_not, melody_wind, edgewalker
 *   ├─ Petting Chain: gentle_touch → devoted_companion
 *   ├─ Mood Cluster: All mood level 3 advancements (connected to root)
 *   ├─ Special Interactions: i_love_you_and_me, restless_sit_glow, are_you_mad_at_me
 *   ├─ Dream/Death Chain: noo_luna → at_what_cost → heartless_but_alive
 *   └─ Role/Ability Cluster: sacrilege, mmm_healing_magic, is_this_designer
 */
public class PetsplusAdvancementProvider extends FabricAdvancementProvider {

    public PetsplusAdvancementProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        super(output, registryLookup);
    }

    @Override
    public void generateAdvancement(RegistryWrapper.WrapperLookup wrapperLookup, Consumer<AdvancementEntry> consumer) {
        // ========================================
        // ROOT ADVANCEMENT
        // ========================================
        AdvancementEntry firstPet = Advancement.Builder.create()
            .display(
                Items.BONE,
                Text.translatable("petsplus.adv.first_pet.title"),
                Text.translatable("petsplus.adv.first_pet.desc"),
                Identifier.ofVanilla("textures/block/smooth_stone.png"),
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("has_pet", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_LEVEL, 
                new PetLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(1),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":first_pet");

        // ========================================
        // LEVELING PROGRESSION CHAIN
        // ========================================
        AdvancementEntry trialReady = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.GOLD_INGOT,
                Text.translatable("petsplus.adv.trial_ready.title"),
                Text.translatable("petsplus.adv.trial_ready.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("level_10", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_LEVEL,
                new PetLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(10),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":trial_ready");

        AdvancementEntry evenBester = Advancement.Builder.create()
            .parent(trialReady)
            .display(
                Items.NETHERITE_INGOT,
                Text.translatable("petsplus.adv.even_bester.title"),
                Text.translatable("petsplus.adv.even_bester.desc"),
                null,
                AdvancementFrame.CHALLENGE,
                true, true, false
            )
            .criterion("level_30", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_LEVEL,
                new PetLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(30),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":even_bester");

        AdvancementEntry orNot = Advancement.Builder.create()
            .parent(evenBester)
            .display(
                Items.SKELETON_SKULL,
                Text.translatable("petsplus.adv.or_not.title"),
                Text.translatable("petsplus.adv.or_not.desc"),
                null,
                AdvancementFrame.GOAL,
                true, true, false
            )
            .criterion("permanent_death", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_DEATH,
                new PetDeathCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(1),
                    Optional.of(true)
                )))
            .build(consumer, Petsplus.MOD_ID + ":or_not");

        // Role-specific leveling milestones
        AdvancementEntry melodyWind = Advancement.Builder.create()
            .parent(trialReady)
            .display(
                Items.FEATHER,
                Text.translatable("petsplus.adv.melody_wind.title"),
                Text.translatable("petsplus.adv.melody_wind.desc"),
                null,
                AdvancementFrame.GOAL,
                true, true, false
            )
            .criterion("skyrider_20", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_LEVEL,
                new PetLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(20),
                    Optional.empty(),
                    Optional.of("petsplus:skyrider")
                )))
            .build(consumer, Petsplus.MOD_ID + ":melody_wind");

        AdvancementEntry edgewalker = Advancement.Builder.create()
            .parent(trialReady)
            .display(
                Items.ENDER_PEARL,
                Text.translatable("petsplus.adv.edgewalker.title"),
                Text.translatable("petsplus.adv.edgewalker.desc"),
                null,
                AdvancementFrame.GOAL,
                true, true, false
            )
            .criterion("eclipsed_30", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_LEVEL,
                new PetLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(30),
                    Optional.empty(),
                    Optional.of("petsplus:eclipsed")
                )))
            .build(consumer, Petsplus.MOD_ID + ":edgewalker");

        // ========================================
        // PETTING INTERACTION CHAIN
        // ========================================
        AdvancementEntry gentleTouch = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.GOLDEN_CARROT,
                Text.translatable("petsplus.adv.gentle_touch.title"),
                Text.translatable("petsplus.adv.gentle_touch.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("pet_once", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_INTERACTION,
                new PetInteractionCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(PetInteractionCriterion.INTERACTION_PETTING),
                    Optional.of(1),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":gentle_touch");

        AdvancementEntry devotedCompanion = Advancement.Builder.create()
            .parent(gentleTouch)
            .display(
                Items.ENCHANTED_GOLDEN_APPLE,
                Text.translatable("petsplus.adv.devoted_companion.title"),
                Text.translatable("petsplus.adv.devoted_companion.desc"),
                null,
                AdvancementFrame.CHALLENGE,
                true, true, false
            )
            .criterion("pet_100_times", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_INTERACTION,
                new PetInteractionCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(PetInteractionCriterion.INTERACTION_PETTING),
                    Optional.of(100),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":devoted_companion");

        // ========================================
        // MOOD LEVEL 3 CLUSTER
        // ========================================
        AdvancementEntry hereComesSunbeam = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.SUNFLOWER,
                Text.translatable("petsplus.adv.here_comes_the_sunbeam.title"),
                Text.translatable("petsplus.adv.here_comes_the_sunbeam.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("happy_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("HAPPY"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":here_comes_the_sunbeam");

        AdvancementEntry playfulNow = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.SLIME_BALL,
                Text.translatable("petsplus.adv.please_fcking_play_with_me_now.title"),
                Text.translatable("petsplus.adv.please_fcking_play_with_me_now.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("playful_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("PLAYFUL"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":please_fcking_play_with_me_now");

        AdvancementEntry indianaBones = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.BRUSH,
                Text.translatable("petsplus.adv.indiana_bones_zoomies.title"),
                Text.translatable("petsplus.adv.indiana_bones_zoomies.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("curious_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("CURIOUS"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":indiana_bones_zoomies");

        AdvancementEntry bondVoyage = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.LEAD,
                Text.translatable("petsplus.adv.bond_voyage.title"),
                Text.translatable("petsplus.adv.bond_voyage.desc"),
                null,
                AdvancementFrame.GOAL,
                true, true, false
            )
            .criterion("bonded_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("BONDED"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":bond_voyage");

        AdvancementEntry beMoreChill = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.BLUE_ICE,
                Text.translatable("petsplus.adv.be_more_chilllllll.title"),
                Text.translatable("petsplus.adv.be_more_chilllllll.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("calm_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("CALM"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":be_more_chilllllll");

        AdvancementEntry heartsOnPyre = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.BLAZE_POWDER,
                Text.translatable("petsplus.adv.hearts_on_pyre.title"),
                Text.translatable("petsplus.adv.hearts_on_pyre.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("passionate_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("PASSIONATE"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":hearts_on_pyre");

        AdvancementEntry yugenSigh = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.AMETHYST_SHARD,
                Text.translatable("petsplus.adv.yugen_sigh_sigh.title"),
                Text.translatable("petsplus.adv.yugen_sigh_sigh.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("yugen_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("YUGEN"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":yugen_sigh_sigh");

        AdvancementEntry finnishHim = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.IRON_CHESTPLATE,
                Text.translatable("petsplus.adv.finnish_him.title"),
                Text.translatable("petsplus.adv.finnish_him.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("sisu_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("SISU"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":finnish_him");

        AdvancementEntry wishYouWereHere = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.ECHO_SHARD,
                Text.translatable("petsplus.adv.wish_you_were_here_fur_real.title"),
                Text.translatable("petsplus.adv.wish_you_were_here_fur_real.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("saudade_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("SAUDADE"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":wish_you_were_here_fur_real");

        AdvancementEntry afraidiana = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.PHANTOM_MEMBRANE,
                Text.translatable("petsplus.adv.afraidiana_grande.title"),
                Text.translatable("petsplus.adv.afraidiana_grande.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("afraid_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("AFRAID"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":afraidiana_grande");

        AdvancementEntry guardianGrrr = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.SHIELD,
                Text.translatable("petsplus.adv.guardian_of_the_grrr_laxy.title"),
                Text.translatable("petsplus.adv.guardian_of_the_grrr_laxy.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("protective_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("PROTECTIVE"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":guardian_of_the_grrr_laxy");

        // ========================================
        // SPECIAL MOOD INTERACTIONS
        // ========================================
        AdvancementEntry restlessSitGlow = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.GLOW_BERRIES,
                Text.translatable("petsplus.adv.restless_sit_glow.title"),
                Text.translatable("petsplus.adv.restless_sit_glow.desc"),
                null,
                AdvancementFrame.GOAL,
                true, true, false
            )
            .criterion("restless_to_happy", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_TRANSITION,
                new PetMoodTransitionCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("RESTLESS"),
                    Optional.of(3),
                    Optional.of("HAPPY"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":restless_sit_glow");

        AdvancementEntry areYouMadAtMe = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.ROSE_BUSH,
                Text.translatable("petsplus.adv.are_you_mad_at_me.title"),
                Text.translatable("petsplus.adv.are_you_mad_at_me.desc"),
                null,
                AdvancementFrame.GOAL,
                true, true, false
            )
            .criterion("angry_cooldown", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_TRANSITION,
                new PetMoodTransitionCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("ANGRY"),
                    Optional.of(3),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":are_you_mad_at_me");

        // ========================================
        // STARGAZE & SPECIAL INTERACTIONS
        // ========================================
        AdvancementEntry iLoveYouAndMe = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.SPYGLASS,
                Text.translatable("petsplus.adv.i_love_you_and_me.title"),
                Text.translatable("petsplus.adv.i_love_you_and_me.desc"),
                null,
                AdvancementFrame.GOAL,
                true, true, false
            )
            .criterion("stargaze_complete", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_INTERACTION,
                new PetInteractionCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(PetInteractionCriterion.INTERACTION_STARGAZING),
                    Optional.of(1),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":i_love_you_and_me");

        // ========================================
        // DREAM ESCAPE / SACRIFICE CHAIN
        // ========================================
        AdvancementEntry nooLuna = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.TOTEM_OF_UNDYING,
                Text.translatable("petsplus.adv.noo_luna.title"),
                Text.translatable("petsplus.adv.noo_luna.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("dream_escape", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_INTERACTION,
                new PetInteractionCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(PetInteractionCriterion.INTERACTION_DREAM_ESCAPE),
                    Optional.of(1),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":noo_luna");

        AdvancementEntry atWhatCost = Advancement.Builder.create()
            .parent(nooLuna)
            .display(
                Items.CRYING_OBSIDIAN,
                Text.translatable("petsplus.adv.at_what_cost.title"),
                Text.translatable("petsplus.adv.at_what_cost.desc"),
                null,
                AdvancementFrame.GOAL,
                true, true, false
            )
            .criterion("dream_escape_2", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_INTERACTION,
                new PetInteractionCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(PetInteractionCriterion.INTERACTION_DREAM_ESCAPE),
                    Optional.of(2),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":at_what_cost");

        AdvancementEntry heartlessButAlive = Advancement.Builder.create()
            .parent(atWhatCost)
            .display(
                Items.WITHER_SKELETON_SKULL,
                Text.translatable("petsplus.adv.heartless_but_alive.title"),
                Text.translatable("petsplus.adv.heartless_but_alive.desc"),
                null,
                AdvancementFrame.CHALLENGE,
                true, true, false
            )
            .criterion("dream_escape_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_INTERACTION,
                new PetInteractionCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(PetInteractionCriterion.INTERACTION_DREAM_ESCAPE),
                    Optional.of(3),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":heartless_but_alive");

        // ========================================
        // ROLE-SPECIFIC ACHIEVEMENTS
        // ========================================
        AdvancementEntry sacrilege = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.NETHERITE_CHESTPLATE,
                Text.translatable("petsplus.adv.sacrilege.title"),
                Text.translatable("petsplus.adv.sacrilege.desc"),
                null,
                AdvancementFrame.GOAL,
                true, true, false
            )
            .criterion("guardian_tank_damage", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_STAT_THRESHOLD,
                new PetStatThresholdCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(PetStatThresholdCriterion.STAT_GUARDIAN_DAMAGE),
                    Optional.of(1000.0f),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":sacrilege");

        AdvancementEntry healingMagic = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.GLISTERING_MELON_SLICE,
                Text.translatable("petsplus.adv.mmm_healing_magic.title"),
                Text.translatable("petsplus.adv.mmm_healing_magic.desc"),
                null,
                AdvancementFrame.GOAL,
                true, true, false
            )
            .criterion("support_heal_allies", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_STAT_THRESHOLD,
                new PetStatThresholdCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(PetStatThresholdCriterion.STAT_ALLIES_HEALED),
                    Optional.of(5.0f),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":mmm_healing_magic");

        AdvancementEntry isThisDesigner = Advancement.Builder.create()
            .parent(trialReady)
            .display(
                Items.ENCHANTED_BOOK,
                Text.translatable("petsplus.adv.is_this_designer.title"),
                Text.translatable("petsplus.adv.is_this_designer.desc"),
                null,
                AdvancementFrame.CHALLENGE,
                true, true, false
            )
            .criterion("ability_max_rank", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_STAT_THRESHOLD,
                new PetStatThresholdCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("ability_max_rank"),
                    Optional.of(1.0f),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":is_this_designer");
    }
}
