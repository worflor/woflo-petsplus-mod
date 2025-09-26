package woflo.petsplus.mixin;

import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import woflo.petsplus.events.EmotionsEventHandler;

/**
 * Hooks weather and time transitions so the emotion system can react exactly
 * when Minecraft flips those flags instead of polling every world tick.
 */
@Mixin(ServerWorld.class)
public abstract class ServerWorldWeatherMixin {

    @Unique
    private boolean petsplus$prevRaining;

    @Unique
    private boolean petsplus$prevThundering;

    @Unique
    private long petsplus$prevTimeOfDay;

    @Inject(method = "setWeather", at = @At("HEAD"))
    private void petsplus$captureWeatherState(int clearDuration, int rainDuration, boolean raining, boolean thundering, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        this.petsplus$prevRaining = world.isRaining();
        this.petsplus$prevThundering = world.isThundering();
    }

    @Inject(method = "setWeather", at = @At("TAIL"))
    private void petsplus$notifyWeatherChange(int clearDuration, int rainDuration, boolean raining, boolean thundering, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (raining != this.petsplus$prevRaining || thundering != this.petsplus$prevThundering) {
            EmotionsEventHandler.handleWeatherUpdated(world, this.petsplus$prevRaining, this.petsplus$prevThundering, raining, thundering);
        }
    }

    @Inject(method = "setTimeOfDay", at = @At("HEAD"))
    private void petsplus$captureTime(long timeOfDay, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        this.petsplus$prevTimeOfDay = world.getTimeOfDay();
    }

    @Inject(method = "setTimeOfDay", at = @At("TAIL"))
    private void petsplus$notifyTime(long timeOfDay, CallbackInfo ci) {
        ServerWorld world = (ServerWorld) (Object) this;
        if (timeOfDay != this.petsplus$prevTimeOfDay) {
            EmotionsEventHandler.handleTimeOfDayUpdated(world, this.petsplus$prevTimeOfDay, timeOfDay);
        }
    }
}
