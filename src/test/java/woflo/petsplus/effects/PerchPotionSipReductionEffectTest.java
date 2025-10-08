package woflo.petsplus.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import woflo.petsplus.api.EffectContext;
import woflo.petsplus.api.TriggerContext;
import woflo.petsplus.api.registry.PetRoleType;
import woflo.petsplus.roles.support.SupportPotionUtils;
import woflo.petsplus.state.PetComponent;

class PerchPotionSipReductionEffectTest {

    @Test
    void executeStoresDiscountFromConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("discount_percent", 0.35);
        config.addProperty("linger_ticks", 120);
        PerchPotionSipReductionEffect effect = new PerchPotionSipReductionEffect(config);

        ServerWorld world = Mockito.mock(ServerWorld.class);
        Mockito.when(world.getTime()).thenReturn(100L);

        MobEntity pet = Mockito.mock(MobEntity.class);
        Mockito.when(pet.getEntityWorld()).thenReturn(world);
        PlayerEntity owner = Mockito.mock(PlayerEntity.class);

        PetComponent component = Mockito.mock(PetComponent.class);
        Mockito.when(component.hasRole(PetRoleType.SUPPORT)).thenReturn(true);
        Mockito.when(component.isOwnedBy(owner)).thenReturn(true);
        Mockito.when(component.getPetEntity()).thenReturn(pet);

        Map<String, Object> state = new HashMap<>();
        Mockito.doAnswer(invocation -> {
            state.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(component).setStateData(Mockito.anyString(), Mockito.any());
        Mockito.when(component.getStateData(Mockito.anyString(), Mockito.eq(Double.class)))
            .thenAnswer(invocation -> (Double) state.get(invocation.getArgument(0)));
        Mockito.when(component.getStateData(Mockito.anyString(), Mockito.eq(Long.class)))
            .thenAnswer(invocation -> (Long) state.get(invocation.getArgument(0)));

        try (MockedStatic<PetComponent> componentStatic = Mockito.mockStatic(PetComponent.class)) {
            componentStatic.when(() -> PetComponent.get(pet)).thenReturn(component);

            TriggerContext trigger = new TriggerContext(world, pet, owner, "interval_while_active");
            EffectContext context = new EffectContext(world, pet, owner, trigger);

            assertTrue(effect.execute(context), "Effect should execute when the pet and owner are valid");
        }

        assertEquals(0.35, state.get(SupportPotionUtils.STATE_PERCH_SIP_DISCOUNT));
        assertEquals(0.65, state.get(SupportPotionUtils.STATE_PERCH_SIP_MULTIPLIER));
        assertEquals(220L, state.get(SupportPotionUtils.STATE_PERCH_SIP_EXPIRY_TICK));
        assertEquals(0.65, SupportPotionUtils.getConsumptionPerPulse(component), 1.0E-6);
    }
}

