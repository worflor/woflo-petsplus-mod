package woflo.petsplus.ai.goals.follow;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import woflo.petsplus.ai.goals.GoalDefinition;
import woflo.petsplus.ai.goals.GoalIds;
import woflo.petsplus.ai.goals.GoalMovementConfig;
import woflo.petsplus.ai.goals.GoalRegistry;
import woflo.petsplus.ai.movement.MovementCommand;
import woflo.petsplus.ai.movement.TargetDistanceModifier;
import woflo.petsplus.state.PetComponent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HesitateInCombatGoalWeightRefreshTest {

    private static final sun.misc.Unsafe UNSAFE = fetchUnsafe();

    @AfterEach
    void resetRegistry() {
        GoalRegistry.clearDataDriven();
    }

    @Test
    void modifiersRefreshWhenWeightChanges() throws Exception {
        GoalDefinition definition = GoalRegistry.require(GoalIds.HESITATE_IN_COMBAT);

        PetComponent.MovementDirector director = new PetComponent.MovementDirector(null);
        PetComponent component = instantiateComponent(director);
        HesitateInCombatGoal goal = instantiateGoal(definition, director, component);

        invoke(goal, "onStartGoal");

        double baseDistance = FollowTuning.resolveFollowDistance(component);
        double baseSpeed = 0.6d;

        MovementCommand initial = director.previewMovement(Vec3d.ZERO, baseDistance, baseSpeed);
        assertEquals(baseSpeed + 0.2d, initial.speed(), 1.0e-6);
        double desired = Math.max(2.5d, baseDistance * 0.6d);
        assertEquals(desired, initial.desiredDistance(), 1.0e-6);

        float refreshedWeight = 0.25f;
        GoalRegistry.registerDataDriven(duplicate(definition), GoalMovementConfig.influencer(refreshedWeight));

        invoke(goal, "onTickGoal");
        MovementCommand refreshed = director.previewMovement(Vec3d.ZERO, baseDistance, baseSpeed);
        assertEquals(baseSpeed + 0.2d * refreshedWeight, refreshed.speed(), 1.0e-6);
        TargetDistanceModifier refreshedModifier = distanceModifier(director);
        assertEquals(desired - baseDistance, refreshedModifier.distanceOffset(), 1.0e-6);
        assertEquals(refreshedWeight, refreshedModifier.weight(), 1.0e-6);

        GoalRegistry.registerDataDriven(duplicate(definition), GoalMovementConfig.influencer(0.0f));

        invoke(goal, "onTickGoal");
        MovementCommand cleared = director.previewMovement(Vec3d.ZERO, baseDistance, baseSpeed);
        assertEquals(baseSpeed, cleared.speed(), 1.0e-6);
        assertEquals(baseDistance, cleared.desiredDistance(), 1.0e-6);
        TargetDistanceModifier clearedModifier = distanceModifier(director);
        org.junit.jupiter.api.Assertions.assertNull(clearedModifier);
    }

    private static GoalDefinition duplicate(GoalDefinition source) {
        return new GoalDefinition(
            source.id(),
            source.category(),
            source.priority(),
            source.minCooldownTicks(),
            source.maxCooldownTicks(),
            source.requirement(),
            source.energyRange() == null ? new Vec2f(0.0f, 1.0f) : source.energyRange(),
            source.idleStaminaBias(),
            source.socialIdleBias(),
            source.marksMajorActivity(),
            source.factory()
        );
    }

    private static HesitateInCombatGoal instantiateGoal(GoalDefinition definition,
                                                        PetComponent.MovementDirector director,
                                                        PetComponent component) throws Exception {
        HesitateInCombatGoal goal = (HesitateInCombatGoal) UNSAFE.allocateInstance(HesitateInCombatGoal.class);

        setField(goal, HesitateInCombatGoal.class, "lastAppliedWeight", Double.NaN);
        setField(goal, Goal.class, "controls", EnumSet.noneOf(Goal.Control.class));
        setField(goal, woflo.petsplus.ai.goals.AdaptiveGoal.class, "mob", null);
        setField(goal, woflo.petsplus.ai.goals.AdaptiveGoal.class, "goalDefinition", definition);
        setField(goal, woflo.petsplus.ai.goals.AdaptiveGoal.class, "goalId", definition.id());
        setField(goal, woflo.petsplus.ai.goals.AdaptiveGoal.class, "lastResolvedDefinition", definition);
        setField(goal, woflo.petsplus.ai.goals.AdaptiveGoal.class, "petComponent", component);
        setField(goal, woflo.petsplus.ai.goals.AdaptiveGoal.class, "movementDirector", director);
        return goal;
    }

    private static PetComponent instantiateComponent(PetComponent.MovementDirector director) throws Exception {
        StubPetComponent component = (StubPetComponent) UNSAFE.allocateInstance(StubPetComponent.class);
        component.installDirector(director);
        component.assignRoleId(Identifier.of("petsplus", "guardian"));
        return component;
    }

    private static Object invoke(Object target, String method) throws Exception {
        Method m = HesitateInCombatGoal.class.getDeclaredMethod(method);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static void setField(Object instance, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        long offset = UNSAFE.objectFieldOffset(field);
        if (value instanceof Double doubleValue) {
            UNSAFE.putDouble(instance, offset, doubleValue);
        } else {
            UNSAFE.putObject(instance, offset, value);
        }
    }

    private static TargetDistanceModifier distanceModifier(PetComponent.MovementDirector director) throws Exception {
        Field field = PetComponent.MovementDirector.class.getDeclaredField("distanceModifiers");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Identifier, TargetDistanceModifier> map = (Map<Identifier, TargetDistanceModifier>) field.get(director);
        return map.get(GoalIds.HESITATE_IN_COMBAT);
    }

    private static sun.misc.Unsafe fetchUnsafe() {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (sun.misc.Unsafe) field.get(null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Unable to access Unsafe", error);
        }
    }

    private static final class StubPetComponent extends PetComponent {

        private Identifier roleId = Identifier.of("petsplus", "guardian");
        private MovementDirector director;

        private StubPetComponent() {
            super((net.minecraft.entity.mob.MobEntity) null);
            throw new AssertionError("Stub");
        }

        void assignRoleId(Identifier roleId) {
            this.roleId = roleId;
        }

        void installDirector(MovementDirector director) {
            this.director = director;
        }

        @Override
        public Identifier getRoleId() {
            return roleId;
        }

        @Override
        public PlayerEntity getOwner() {
            return null;
        }

        @Override
        public MovementDirector getMovementDirector() {
            return director;
        }
    }
}
