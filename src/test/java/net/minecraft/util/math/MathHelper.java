package net.minecraft.util.math;

/** Minimal math helper utilities used in tests. */
public final class MathHelper {
    private MathHelper() {}

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static float unpackDegrees(byte packed) {
        return packed * (360.0f / 256.0f);
    }

    public static byte packDegrees(float degrees) {
        return (byte) Math.round(degrees * 256.0f / 360.0f);
    }

    public static float wrapDegrees(float value) {
        return (float) wrapDegrees((double) value);
    }

    public static double wrapDegrees(double value) {
        double wrapped = value % 360.0;
        if (wrapped >= 180.0) {
            wrapped -= 360.0;
        }
        if (wrapped < -180.0) {
            wrapped += 360.0;
        }
        return wrapped;
    }

    public static int smallestEncompassingPowerOfTwo(int value) {
        if (value <= 1) {
            return 1;
        }
        int result = 1;
        while (result < value) {
            result <<= 1;
        }
        return result;
    }

    public static int floorLog2(int value) {
        if (value <= 0) {
            return 0;
        }
        return 31 - Integer.numberOfLeadingZeros(value);
    }
}
