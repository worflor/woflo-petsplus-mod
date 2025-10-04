package woflo.petsplus.effects;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.roles.guardian.GuardianAegisProtocolManager;
import woflo.petsplus.ui.UIFeedbackManager;

/**
 * Effect that awards Guardian Aegis Protocol stacks after a successful redirect.
 */
public class GuardianAegisProtocolEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "guardian_aegis_protocol");

    private final int maxStacks;
    private final int durationTicks;

    public GuardianAegisProtocolEffect(int maxStacks, int durationTicks) {
        this.maxStacks = Math.max(1, maxStacks);
        this.durationTicks = Math.max(40, durationTicks);
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        MobEntity pet = context.getPet();
        if (pet == null || !pet.isAlive()) {
            return false;
        }
        if (!(context.getOwner() instanceof ServerPlayerEntity owner)) {
            return false;
        }

        if (!(owner.getWorld() instanceof ServerWorld ownerWorld) || ownerWorld != world) {
            return false;
        }

        int stacks = GuardianAegisProtocolManager.incrementStacks(pet, owner, maxStacks, durationTicks);
        if (stacks <= 0) {
            return false;
        }

        // Removed aegis spam - will add particle/sound in ability JSON
        context.withData("guardian_aegis_stacks", stacks);
        return true;
    }

    @Override
    public int getDurationTicks() {
        return durationTicks;
    }
}
