package woflo.petsplus.pet;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.text.Text;

/**
 * Enum representing the result of a pet ownership transfer operation.
 * Each result has an associated translation key for user feedback.
 */
public enum TransferResult {
    SUCCESS("petsplus.trading.success"),
    NOT_OWNED("petsplus.trading.not_owned"),
    NOT_LEASHED("petsplus.trading.not_leashed"),
    OUT_OF_RANGE("petsplus.trading.out_of_range"),
    RECIPIENT_INVALID("petsplus.trading.recipient_invalid"),
    INCOMPATIBLE("petsplus.trading.incompatible"),
    NO_ELIGIBLE_PETS("petsplus.trading.no_eligible_pets"),
    SERVER_ERROR("petsplus.trading.server_error");
    
    private final String translationKey;
    
    TransferResult(String translationKey) {
        this.translationKey = translationKey;
    }
    
    public String getTranslationKey() {
        return translationKey;
    }
    
    public Text getLocalizedText(Object... args) {
        return Text.translatable(translationKey, args);
    }
    
    /**
     * Context information for a transfer result, including the pet involved
     * and any additional message arguments for localization.
     */
    public static class TransferContext {
        private final MobEntity pet;
        private final String petName;
        private final Object[] messageArgs;
        
        public TransferContext(MobEntity pet, String petName, Object... messageArgs) {
            this.pet = pet;
            this.petName = petName;
            this.messageArgs = messageArgs;
        }
        
        public MobEntity getPet() { 
            return pet; 
        }
        
        public String getPetName() { 
            return petName; 
        }
        
        public Object[] getMessageArgs() { 
            return messageArgs; 
        }
    }
}
