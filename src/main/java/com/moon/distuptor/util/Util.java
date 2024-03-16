package com.moon.distuptor.util;

import com.moon.distuptor.EventProcessor;
import com.moon.distuptor.Sequence;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

public final class Util {
    public static int ceilingNextPowerOfTwo(final int x) {
        return 1 << (32 - Integer.numberOfLeadingZeros(x - 1));
    }


    public static long getMinimumSequence(final Sequence[] sequences) {
        return getMinimumSequence(sequences, Long.MAX_VALUE);
    }


    public static long getMinimumSequence(final Sequence[] sequences, long minimum) {
        for (int i = 0, n = sequences.length; i < n; i++) {
            long value = sequences[i].get();
            minimum = Math.min(minimum, value);
        }

        return minimum;
    }


    public static Sequence[] getSequencesFor(final EventProcessor... processors) {
        Sequence[] sequences = new Sequence[processors.length];
        for (int i = 0; i < sequences.length; i++) {
            sequences[i] = processors[i].getSequence();
        }

        return sequences;
    }

    private static final Unsafe THE_UNSAFE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Unsafe.class, MethodHandles.lookup());
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            THE_UNSAFE = (Unsafe) lookup.unreflectGetter(theUnsafe).invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException("Unable to load unsafe", e);
        }
    }


    public static Unsafe getUnsafe() {
        return THE_UNSAFE;
    }


    public static int log2(int i) {
        int r = 0;
        while ((i >>= 1) != 0) {
            ++r;
        }
        return r;
    }
}