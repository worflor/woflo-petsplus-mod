package net.minecraft.text;

/** Minimal mutable text stub. */
public class MutableText extends Text.SimpleText {
    public MutableText(String value) {
        super(value);
    }

    public MutableText append(Text text) {
        return this;
    }
}
