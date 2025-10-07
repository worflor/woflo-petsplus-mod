package net.minecraft.util.math.random;

/**
 * Minimal random interface implementation mirroring the game API for tests.
 */
public interface Random {

    static Random create() {
        return new SimpleRandom(new java.util.Random());
    }

    static Random create(long seed) {
        return new SimpleRandom(new java.util.Random(seed));
    }

    boolean nextBoolean();

    float nextFloat();

    double nextDouble();

    int nextInt();

    int nextInt(int bound);

    class SimpleRandom implements Random {
        private final java.util.Random delegate;

        SimpleRandom(java.util.Random delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean nextBoolean() {
            return delegate.nextBoolean();
        }

        @Override
        public float nextFloat() {
            return delegate.nextFloat();
        }

        @Override
        public double nextDouble() {
            return delegate.nextDouble();
        }

        @Override
        public int nextInt() {
            return delegate.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            return delegate.nextInt(bound);
        }
    }
}
