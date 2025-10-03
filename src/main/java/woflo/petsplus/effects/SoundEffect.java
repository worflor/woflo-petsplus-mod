package woflo.petsplus.effects;

import net.minecraft.entity.Entity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import woflo.petsplus.api.Effect;
import woflo.petsplus.api.EffectContext;

/**
 * Plays a sound at entity or victim location.
 * Prioritizes noteblock instruments for subtle, musical feedback.
 * Falls back to vanilla sounds when needed for specific effects.
 */
public class SoundEffect implements Effect {
    private static final Identifier ID = Identifier.of("petsplus", "sound_effect");
    
    private final String soundName;
    private final String targetEntity; // "pet", "owner", "victim"
    private final float volume;
    private final float pitch;
    private final String category;
    
    // Noteblock instrument names for easy reference
    // Use these in ability JSONs: "note_bass", "note_bell", "note_chime", etc.
    
    public SoundEffect(String soundName, String targetEntity, float volume, float pitch, String category) {
        this.soundName = soundName;
        this.targetEntity = targetEntity != null ? targetEntity : "victim";
        this.volume = volume;
        this.pitch = pitch;
        this.category = category != null ? category : "neutral";
    }
    
    @Override
    public Identifier getId() {
        return ID;
    }
    
    @Override
    public boolean execute(EffectContext context) {
        if (!(context.getWorld() instanceof ServerWorld serverWorld)) {
            return false;
        }
        
        Entity target = resolveTarget(context);
        if (target == null) {
            return false;
        }
        
        SoundEvent sound = parseSoundEvent(soundName);
        if (sound == null) {
            return false;
        }
        
        SoundCategory soundCategory = parseSoundCategory(category);
        Vec3d pos = target.getPos();
        
        serverWorld.playSound(
            null, // player (null = all can hear)
            pos.x,
            pos.y,
            pos.z,
            sound,
            soundCategory,
            volume,
            pitch
        );
        
        return true;
    }
    
    private Entity resolveTarget(EffectContext context) {
        return switch (targetEntity.toLowerCase()) {
            case "pet" -> context.getPet();
            case "owner" -> context.getOwner();
            case "victim" -> context.getData("victim", Entity.class);
            default -> null;
        };
    }
    
    private SoundCategory parseSoundCategory(String cat) {
        return switch (cat.toLowerCase()) {
            case "master" -> SoundCategory.MASTER;
            case "music" -> SoundCategory.MUSIC;
            case "record" -> SoundCategory.RECORDS;
            case "weather" -> SoundCategory.WEATHER;
            case "block" -> SoundCategory.BLOCKS;
            case "hostile" -> SoundCategory.HOSTILE;
            case "neutral" -> SoundCategory.NEUTRAL;
            case "player" -> SoundCategory.PLAYERS;
            case "ambient" -> SoundCategory.AMBIENT;
            case "voice" -> SoundCategory.VOICE;
            default -> SoundCategory.NEUTRAL;
        };
    }
    
    private SoundEvent parseSoundEvent(String name) {
        // Prioritize noteblock instruments - these are the baseline for abilities
        // Pitch should be controlled via the pitch parameter (0.5 = low, 2.0 = high)
        
        // Helper to unwrap RegistryEntry -> SoundEvent
        var sound = parseSoundEventEntry(name);
        if (sound == null) return null;
        
        // Some are direct SoundEvent, some are RegistryEntry - handle both
        if (sound instanceof RegistryEntry<?> entry) {
            return (SoundEvent) entry.value();
        }
        return (SoundEvent) sound;
    }
    
    private Object parseSoundEventEntry(String name) {
        // Prioritize noteblock instruments - these are the baseline for abilities
        // Pitch should be controlled via the pitch parameter (0.5 = low, 2.0 = high)
        return switch (name.toLowerCase()) {
            // === NOTEBLOCK INSTRUMENTS (Primary feedback system) ===
            // Deep, resonant tones - good for heavy hits, shields, defense
            case "note_bass", "note_bassdrum" -> SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM;
            
            // Percussive, sharp - good for crits, executions, finishers  
            case "note_snare" -> SoundEvents.BLOCK_NOTE_BLOCK_SNARE;
            case "note_hat" -> SoundEvents.BLOCK_NOTE_BLOCK_HAT;
            
            // Melodic, pleasant - good for buffs, marks, positive feedback
            case "note_bell" -> SoundEvents.BLOCK_NOTE_BLOCK_BELL;
            case "note_chime" -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME;
            case "note_glockenspiel", "note_iron_xylophone" -> SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE;
            case "note_xylophone" -> SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE;
            
            // Ethereal, magical - good for teleports, phase abilities, enchantments
            case "note_pling" -> SoundEvents.BLOCK_NOTE_BLOCK_PLING;
            case "note_flute" -> SoundEvents.BLOCK_NOTE_BLOCK_FLUTE;
            
            // Warm, organic - good for pet interactions, healing, support
            case "note_guitar", "note_pluck" -> SoundEvents.BLOCK_NOTE_BLOCK_GUITAR;
            case "note_banjo" -> SoundEvents.BLOCK_NOTE_BLOCK_BANJO;
            case "note_harp" -> SoundEvents.BLOCK_NOTE_BLOCK_HARP;
            
            // Atmospheric, ambient - good for stealth, scouting, detection
            case "note_bit" -> SoundEvents.BLOCK_NOTE_BLOCK_BIT;
            case "note_didgeridoo" -> SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO;
            
            // Brass, powerful - good for commands
            case "note_cow_bell" -> SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL;
            
            // === VANILLA SOUNDS (Sparingly for specific effects) ===
            // Only use these when noteblocks can't capture the feeling
            
            // Amethyst - crystalline, magical resonance
            case "amethyst_chime" -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME;
            case "amethyst_hit" -> SoundEvents.BLOCK_AMETHYST_BLOCK_HIT;
            
            // Bells - important announcements
            case "bell" -> SoundEvents.BLOCK_BELL_USE;
            case "bell_resonate" -> SoundEvents.BLOCK_BELL_RESONATE;
            
            // XP - level ups, unlocks
            case "xp_pickup", "orb_pickup" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "levelup" -> SoundEvents.ENTITY_PLAYER_LEVELUP;
            
            // Teleport/Phase
            case "enderman_teleport", "teleport" -> SoundEvents.ENTITY_ENDERMAN_TELEPORT;
            case "portal" -> SoundEvents.BLOCK_PORTAL_TRIGGER;
            
            // Dark/Cursed abilities only
            case "warden_heartbeat" -> SoundEvents.ENTITY_WARDEN_HEARTBEAT;
            case "elder_guardian_curse", "curse" -> SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE;
            
            default -> null;
        };
    }
}
