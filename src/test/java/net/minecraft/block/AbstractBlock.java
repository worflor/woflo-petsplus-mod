package net.minecraft.block;

/** Minimal AbstractBlock stub for tests. */
public class AbstractBlock {
    protected AbstractBlock(Settings settings) {
    }

    public static class Settings {
        public static Settings create() {
            return new Settings();
        }
    }
}
