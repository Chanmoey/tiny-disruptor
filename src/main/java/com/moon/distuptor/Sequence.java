package com.moon.distuptor;

import com.moon.distuptor.util.Util;
import sun.misc.Unsafe;

/**
 * @author Chanmoey
 * Create at 2024/3/14
 */

class LhsPadding {   //在父类填充空字节
    protected long p1, p2, p3, p4, p5, p6, p7;
}

class Value extends LhsPadding {
    // value前后都有7个空的long整数，也就是56个空白字节，加上当前的value正好是64个字节
    // 高速缓存一行缓存64个字节，这样不管怎么读取这个value，它都会自己独占一个缓存行，不会出现伪共享了
    protected volatile long value;
}

class RhsPadding extends Value {   //在子类填充空字节
    protected long p9, p10, p11, p12, p13, p14, p15;
}

public class Sequence extends RhsPadding {

    static final long INITIAL_VALUE = -1L;
    //Unsafe对象
    private static final Unsafe UNSAFE;
    //value在对象中的内存偏移量
    private static final long VALUE_OFFSET;

    static {
        UNSAFE = Util.getUnsafe();
        try {   //该方法的作用在环形数组类也见过了，就是得到value在对象中的内存偏移量，这样就能直接根据内存偏移量，让Unsafe对象直接操纵这个变量了
            VALUE_OFFSET = UNSAFE.objectFieldOffset(Value.class.getDeclaredField("value"));
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    //构造方法
    public Sequence() {
        this(INITIAL_VALUE);
    }

    //设置value的初始值，也就是-1L
    public Sequence(final long initialValue) {
        UNSAFE.putOrderedLong(this, VALUE_OFFSET, initialValue);
    }

    //返回value
    public long get() {
        return value;
    }

    //设置value的值
    public void set(final long value) {   // putOrderedLong方法并不会保证内存的立即可见性，只会保证不被指令重拍，也就是说，更新了value，该值可能并不会立刻被其他线程可见
        UNSAFE.putOrderedLong(this, VALUE_OFFSET, value);
    }

    public void setVolatile(final long value) {
        UNSAFE.putLongVolatile(this, VALUE_OFFSET, value);
    }

    public boolean compareAndSet(final long expectedValue, final long newValue) {
        return UNSAFE.compareAndSwapLong(this, VALUE_OFFSET, expectedValue, newValue);
    }

    public long incrementAndGet() {
        return addAndGet(1L);
    }

    public long addAndGet(final long increment) {
        long currentValue;
        long newValue;

        do {
            currentValue = get();
            newValue = currentValue + increment;
        }
        while (!compareAndSet(currentValue, newValue));

        return newValue;
    }

    @Override
    public String toString() {
        return Long.toString(get());
    }
}
