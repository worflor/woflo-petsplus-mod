package woflo.petsplus.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementCriterion;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.advancement.AdvancementRequirements;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import woflo.petsplus.Petsplus;
import woflo.petsplus.advancement.AdvancementCriteriaRegistry;
import woflo.petsplus.advancement.criteria.*;

import static woflo.petsplus.advancement.AdvancementStatKeys.ABILITY_MAX_RANK;
import static woflo.petsplus.advancement.AdvancementStatKeys.ABILITY_MAX_RANK_UNLOCKED_VALUE;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Data generator for PetsPlus advancements following the new 6-branch tree structure.
 * 
 * New Tree Structure:
 * - Root: first_pet (bonding with first pet)
 *   ├─ [Bonding Basics] bonding_basics (Intermediate)
 *   │   ├─ gentle_touch
 *   │   ├─ devoted_companion
 *   │   └─ trial_ready
 *   │       └─ bestest_friends_forevererer (Challenge)
 *   │           ├─ or_not
 *   │           └─ is_this_designer
 *   ├─ [Emotional Journey] emotional_journey (Intermediate)
 *   │   ├─ mood_explorer
 *   │   ├─ emotional_mastery
 *   │   ├─ bond_voyage
 *   │   └─ mood_categories (12 mood advancements)
 *   ├─ [Mystical Connections] mystical_connections (Intermediate)
 *   │   ├─ i_love_you_and_me
 *   │   └─ noo_luna
 *   │       ├─ at_what_cost
 *   │       └─ heartless_but_alive
 *   ├─ [Role Specialization] role_specialization (Intermediate)
 *   │   ├─ guardian_path
 *   │   ├─ support_path
 *   │   ├─ skyrider_path
 *   │   └─ eclipsed_path
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
                false, false, false
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
        // [BONDING BASICS] BRANCH
        // ========================================
        AdvancementEntry bondingBasics = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.LEAD,
                Text.translatable("petsplus.adv.bonding_basics.title"),
                Text.translatable("petsplus.adv.bonding_basics.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("pet_5_times", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_INTERACTION,
                new PetInteractionCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(PetInteractionCriterion.INTERACTION_PETTING),
                    Optional.of(5),
                    Optional.empty(),
                    Optional.empty()
                )))
            .criterion("bonded_level_1", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("BONDED"),
                    Optional.of(1),
                    Optional.empty()
                )))
            .requirements(AdvancementRequirements.anyOf(
                java.util.List.of("pet_5_times", "bonded_level_1")))
            .build(consumer, Petsplus.MOD_ID + ":bonding_basics");

        AdvancementEntry gentleTouch = Advancement.Builder.create()
            .parent(bondingBasics)
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
            .parent(bondingBasics)
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

        AdvancementEntry trialReady = Advancement.Builder.create()
            .parent(bondingBasics)
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

        AdvancementEntry bestestFriends = Advancement.Builder.create()
            .parent(trialReady)
            .display(
                Items.NETHERITE_INGOT,
                Text.translatable("petsplus.adv.bestest_friends_forevererer.title"),
                Text.translatable("petsplus.adv.bestest_friends_forevererer.desc"),
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
            .build(consumer, Petsplus.MOD_ID + ":bestest_friends_forevererer");

        // ========================================
        // [EMOTIONAL JOURNEY] BRANCH
        // ========================================
        AdvancementEntry emotionalJourney = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.AMETHYST_SHARD,
                Text.translatable("petsplus.adv.emotional_journey.title"),
                Text.translatable("petsplus.adv.emotional_journey.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("mood_variety", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_VARIETY,
                new PetMoodVarietyCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(5),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":emotional_journey");

        AdvancementEntry moodExplorer = Advancement.Builder.create()
            .parent(emotionalJourney)
            .display(
                Items.COMPASS,
                Text.translatable("petsplus.adv.mood_explorer.title"),
                Text.translatable("petsplus.adv.mood_explorer.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("mood_variety_5", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_VARIETY,
                new PetMoodVarietyCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(10),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":mood_explorer");

        AdvancementEntry emotionalMastery = Advancement.Builder.create()
            .parent(moodExplorer)
            .display(
                Items.DIAMOND,
                Text.translatable("petsplus.adv.emotional_mastery.title"),
                Text.translatable("petsplus.adv.emotional_mastery.desc"),
                null,
                AdvancementFrame.GOAL,
                true, true, false
            )
            .criterion("any_mood_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":emotional_mastery");

        AdvancementEntry bondVoyage = Advancement.Builder.create()
            .parent(emotionalMastery)
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

        // Positive Moods Sub-branch
        AdvancementEntry positiveMoods = Advancement.Builder.create()
            .parent(emotionalMastery)
            .display(
                Items.SUNFLOWER,
                Text.translatable("petsplus.adv.positive_moods.title"),
                Text.translatable("petsplus.adv.positive_moods.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("positive_mood_iii", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("HAPPY"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":positive_moods");

        AdvancementEntry raysOfSunshine = Advancement.Builder.create()
            .parent(positiveMoods)
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
            .parent(positiveMoods)
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

        AdvancementEntry curiousExplorer = Advancement.Builder.create()
            .parent(positiveMoods)
            .display(
                Items.BRUSH,
                Text.translatable("petsplus.adv.curiousity_thrilled_the_cat.title"),
                Text.translatable("petsplus.adv.curiousity_thrilled_the_cat.desc"),
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
            .build(consumer, Petsplus.MOD_ID + ":curiousity_thrilled_the_cat");

        AdvancementEntry passionateHeart = Advancement.Builder.create()
            .parent(positiveMoods)
            .display(
                Items.SLIME_BALL,
                Text.translatable("petsplus.adv.please_fcking_play_with_me_now.title"),
                Text.translatable("petsplus.adv.please_fcking_play_with_me_now.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("passionate_level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("PLAYFUL"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":please_fcking_play_with_me_now");

        // Calm Moods Sub-branch
        AdvancementEntry calmMoods = Advancement.Builder.create()
            .parent(emotionalMastery)
            .display(
                Items.LAPIS_LAZULI,
                Text.translatable("petsplus.adv.calm_moods.title"),
                Text.translatable("petsplus.adv.calm_moods.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("calm_mood_iii", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("CALM"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":calm_moods");

        AdvancementEntry tranquilMind = Advancement.Builder.create()
            .parent(calmMoods)
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

        AdvancementEntry yugenMoment = Advancement.Builder.create()
            .parent(calmMoods)
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

        AdvancementEntry sisuStrength = Advancement.Builder.create()
            .parent(calmMoods)
            .display(
                Items.SHIELD,
                Text.translatable("petsplus.adv.sipping_sisurp_in_my_ride.title"),
                Text.translatable("petsplus.adv.sipping_sisurp_in_my_ride.desc"),
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
            .build(consumer, Petsplus.MOD_ID + ":sipping_sisurp_in_my_ride");

        // Complex Moods Sub-branch
        AdvancementEntry complexMoods = Advancement.Builder.create()
            .parent(emotionalMastery)
            .display(
                Items.ENDER_PEARL,
                Text.translatable("petsplus.adv.complex_moods.title"),
                Text.translatable("petsplus.adv.complex_moods.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("complex_mood_iii", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_LEVEL,
                new PetMoodLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("SAUDADE"),
                    Optional.of(3),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":complex_moods");

        AdvancementEntry saudadeLonging = Advancement.Builder.create()
            .parent(complexMoods)
            .display(
                Items.AMETHYST_SHARD,
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

        AdvancementEntry afraidButBrave = Advancement.Builder.create()
            .parent(complexMoods)
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

        AdvancementEntry guardianSpirit = Advancement.Builder.create()
            .parent(complexMoods)
            .display(
                Items.NETHERITE_CHESTPLATE,
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

        // Mood Transitions Sub-branch
        AdvancementEntry moodTransitions = Advancement.Builder.create()
            .parent(emotionalMastery)
            .display(
                Items.CLOCK,
                Text.translatable("petsplus.adv.mood_transitions.title"),
                Text.translatable("petsplus.adv.mood_transitions.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("any_transition", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_MOOD_TRANSITION,
                new PetMoodTransitionCriterion.Conditions(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":mood_transitions");

        AdvancementEntry restlessToHappy = Advancement.Builder.create()
            .parent(moodTransitions)
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
                    Optional.of(1),
                    Optional.of(2000L)
                )))
            .build(consumer, Petsplus.MOD_ID + ":restless_sit_glow");

        AdvancementEntry angerManagement = Advancement.Builder.create()
            .parent(moodTransitions)
            .display(
                Items.FIRE_CHARGE,
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
                    Optional.of("CALM"),
                    Optional.of(1),
                    Optional.of(400L)
                )))
            .build(consumer, Petsplus.MOD_ID + ":are_you_mad_at_me");

        // ========================================
        // [MYSTICAL CONNECTIONS] BRANCH
        // ========================================
        AdvancementEntry mysticalConnections = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.ENDER_EYE,
                Text.translatable("petsplus.adv.mystical_connections.title"),
                Text.translatable("petsplus.adv.mystical_connections.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("level_3", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_LEVEL,
                new PetLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(3),
                    Optional.empty(),
                    Optional.empty()
                )))
            .criterion("night_interaction", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_INTERACTION,
                new PetInteractionCriterion.Conditions(
                    Optional.empty(),
                    Optional.of(PetInteractionCriterion.INTERACTION_STARGAZING),
                    Optional.of(1),
                    Optional.empty(),
                    Optional.empty()
                )))
            .requirements(AdvancementRequirements.anyOf(
                java.util.List.of("level_3", "night_interaction")))
            .build(consumer, Petsplus.MOD_ID + ":mystical_connections");

        AdvancementEntry iLoveYouAndMe = Advancement.Builder.create()
            .parent(mysticalConnections)
            .display(
                Items.SPYGLASS,
                Text.translatable("petsplus.adv.i_love_you_and_me.title"),
                Text.translatable("petsplus.adv.i_love_you_and_me.desc"),
                null,
                AdvancementFrame.TASK,
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

        // The old "Stargazer Bond" advancement was merged into "I love you and me"

        AdvancementEntry nooLuna = Advancement.Builder.create()
            .parent(mysticalConnections)
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
        // [ROLE SPECIALIZATION] BRANCH
        // ========================================
        AdvancementEntry roleSpecialization = Advancement.Builder.create()
            .parent(firstPet)
            .display(
                Items.DIAMOND_SWORD,
                Text.translatable("petsplus.adv.role_specialization.title"),
                Text.translatable("petsplus.adv.role_specialization.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("level_10_with_role", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_ROLE_LEVEL,
                new PetRoleLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(10),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":role_specialization");

        AdvancementEntry guardianPath = Advancement.Builder.create()
            .parent(roleSpecialization)
            .display(
                Items.SHIELD,
                Text.translatable("petsplus.adv.guardian_path.title"),
                Text.translatable("petsplus.adv.guardian_path.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("guardian_level_15", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_ROLE_LEVEL,
                new PetRoleLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("petsplus:guardian"),
                    Optional.of(15),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":guardian_path");

        AdvancementEntry sacrilege = Advancement.Builder.create()
            .parent(guardianPath)
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

        AdvancementEntry supportPath = Advancement.Builder.create()
            .parent(roleSpecialization)
            .display(
                Items.GLISTERING_MELON_SLICE,
                Text.translatable("petsplus.adv.support_path.title"),
                Text.translatable("petsplus.adv.support_path.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("support_level_15", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_ROLE_LEVEL,
                new PetRoleLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("petsplus:support"),
                    Optional.of(15),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":support_path");

        AdvancementEntry healingMagic = Advancement.Builder.create()
            .parent(supportPath)
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

        AdvancementEntry skyriderPath = Advancement.Builder.create()
            .parent(roleSpecialization)
            .display(
                Items.FEATHER,
                Text.translatable("petsplus.adv.skyrider_path.title"),
                Text.translatable("petsplus.adv.skyrider_path.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("skyrider_level_15", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_ROLE_LEVEL,
                new PetRoleLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("petsplus:skyrider"),
                    Optional.of(15),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":skyrider_path");

        AdvancementEntry melodyWind = Advancement.Builder.create()
            .parent(skyriderPath)
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

        AdvancementEntry eclipsedPath = Advancement.Builder.create()
            .parent(roleSpecialization)
            .display(
                Items.ENDER_PEARL,
                Text.translatable("petsplus.adv.eclipsed_path.title"),
                Text.translatable("petsplus.adv.eclipsed_path.desc"),
                null,
                AdvancementFrame.TASK,
                true, true, false
            )
            .criterion("eclipsed_level_15", new AdvancementCriterion<>(AdvancementCriteriaRegistry.PET_ROLE_LEVEL,
                new PetRoleLevelCriterion.Conditions(
                    Optional.empty(),
                    Optional.of("petsplus:eclipsed"),
                    Optional.of(15),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":eclipsed_path");

        AdvancementEntry edgewalker = Advancement.Builder.create()
            .parent(eclipsedPath)
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
        // ["OR NOT."] BRANCH
        // ========================================
        AdvancementEntry orNot = Advancement.Builder.create()
            .parent(bestestFriends)
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
                    Optional.empty(),
                    Optional.of(true)
                )))
            .build(consumer, Petsplus.MOD_ID + ":or_not");

        // ========================================
        // ADDITIONAL ADVANCEMENTS
        // ========================================
        AdvancementEntry isThisDesigner = Advancement.Builder.create()
            .parent(bestestFriends)
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
                    Optional.of(ABILITY_MAX_RANK),
                    Optional.of(ABILITY_MAX_RANK_UNLOCKED_VALUE),
                    Optional.empty()
                )))
            .build(consumer, Petsplus.MOD_ID + ":is_this_designer");
    }
}
