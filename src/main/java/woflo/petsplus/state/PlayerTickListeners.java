package woflo.petsplus.state;

import woflo.petsplus.effects.MagnetizeDropsAndXpEffect;
import woflo.petsplus.events.EmotionContextCues;
import woflo.petsplus.events.EmotionsEventHandler;
import woflo.petsplus.events.PlayerStateTracker;
import woflo.petsplus.interaction.OwnerAbilitySignalTracker;
import woflo.petsplus.mechanics.StargazeMechanic;
import woflo.petsplus.roles.eclipsed.EclipsedCore;
import woflo.petsplus.roles.guardian.GuardianFortressBondManager;
import woflo.petsplus.roles.scout.ScoutCore;
import woflo.petsplus.roles.striker.StrikerHuntManager;
import woflo.petsplus.ui.ActionBarCueManager;
import woflo.petsplus.ui.BossBarManager;
import woflo.petsplus.ui.PetInspectionManager;

/**
 * Central registration hub for dispatcher-managed listeners. Each subsystem
 * exposes a {@link PlayerTickListener} singleton that self-schedules and
 * cleans up its own per-player state, allowing the dispatcher to invoke them
 * only when work is pending.
 */
public final class PlayerTickListeners {

    private PlayerTickListeners() {}

    public static void registerAll() {
        PlayerTickDispatcher.register(EmotionContextCues.getInstance());

        PlayerTickDispatcher.register(EmotionsEventHandler.ticker());

        PlayerTickDispatcher.register(PlayerStateTracker.getInstance());

        PlayerTickDispatcher.register(OwnerAbilitySignalTracker.getInstance());

        PlayerTickDispatcher.register(StargazeMechanic.getInstance());

        PlayerTickDispatcher.register(ActionBarCueManager.getInstance());

        PlayerTickDispatcher.register(PetInspectionManager.listener());

        PlayerTickDispatcher.register(BossBarManager.getInstance());

        PlayerTickDispatcher.register(MagnetizeDropsAndXpEffect.listener());

        PlayerTickDispatcher.register(EclipsedCore.getInstance());


        PlayerTickDispatcher.register(ScoutCore.getInstance());


        PlayerTickDispatcher.register(GuardianFortressBondManager.ticker());

        PlayerTickDispatcher.register(StrikerHuntManager.getInstance());
    }

}

