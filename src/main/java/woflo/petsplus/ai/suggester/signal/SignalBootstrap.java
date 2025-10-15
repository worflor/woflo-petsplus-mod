package woflo.petsplus.ai.suggester.signal;

import woflo.petsplus.ai.suggester.signal.desirability.AgeDesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.desirability.BondDesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.desirability.EnergyDesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.desirability.EnvironmentDesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.desirability.MemoryDesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.desirability.MoodBlendDesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.desirability.NatureDesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.desirability.VarietyDesirabilitySignal;
import woflo.petsplus.ai.suggester.signal.feasibility.ActiveGoalFeasibilitySignal;
import woflo.petsplus.ai.suggester.signal.feasibility.CombatLockoutFeasibilitySignal;
import woflo.petsplus.ai.suggester.signal.feasibility.MobStateFeasibilitySignal;
import woflo.petsplus.ai.suggester.signal.feasibility.SocialProximityFeasibilitySignal;
import woflo.petsplus.ai.suggester.signal.rules.TrialSpawnerAwarenessSignal;
import woflo.petsplus.ai.suggester.signal.rules.BreezeThreatSignal;
import woflo.petsplus.ai.suggester.signal.rules.BoggedThreatSignal;

public final class SignalBootstrap {
    private static volatile boolean initialized;

    private SignalBootstrap() {
    }

    public static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (SignalBootstrap.class) {
            if (initialized) {
                return;
            }
            registerDefaultsIfNeeded();
            initialized = true;
        }
    }

    private static void registerDefaultsIfNeeded() {
        if (DesirabilitySignalRegistry.isEmpty()) {
            DesirabilitySignalRegistry.register(new NatureDesirabilitySignal());
            DesirabilitySignalRegistry.register(new MoodBlendDesirabilitySignal());
            DesirabilitySignalRegistry.register(new EnvironmentDesirabilitySignal());
            DesirabilitySignalRegistry.register(new EnergyDesirabilitySignal());
            DesirabilitySignalRegistry.register(new AgeDesirabilitySignal());
            DesirabilitySignalRegistry.register(new BondDesirabilitySignal());
            DesirabilitySignalRegistry.register(new VarietyDesirabilitySignal());
            DesirabilitySignalRegistry.register(new MemoryDesirabilitySignal());
            DesirabilitySignalRegistry.register(new TrialSpawnerAwarenessSignal());
            DesirabilitySignalRegistry.register(new BreezeThreatSignal());
            DesirabilitySignalRegistry.register(new BoggedThreatSignal());
        }

        if (FeasibilitySignalRegistry.isEmpty()) {
            FeasibilitySignalRegistry.register(new SocialProximityFeasibilitySignal());
            FeasibilitySignalRegistry.register(new ActiveGoalFeasibilitySignal());
            FeasibilitySignalRegistry.register(new MobStateFeasibilitySignal());
            FeasibilitySignalRegistry.register(new CombatLockoutFeasibilitySignal());
        }
    }

    /**
     * Resets the bootstrapper so tests can configure custom signal sets.
     */
    public static synchronized void resetForTesting() {
        initialized = false;
        DesirabilitySignalRegistry.clear();
        FeasibilitySignalRegistry.clear();
        woflo.petsplus.ai.suggester.signal.rules.SignalRuleRegistry.resetToDefaults();
    }

    /**
     * Marks the bootstrapper as initialized without registering defaults. Tests can
     * combine this with {@link #resetForTesting()} to install bespoke signal sets.
     */
    public static synchronized void markInitializedForTesting() {
        initialized = true;
    }
}
