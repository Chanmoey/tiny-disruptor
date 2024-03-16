package com.moon.distuptor;


import com.moon.distuptor.util.Util;

import java.util.concurrent.locks.LockSupport;

/**
 * @author Chanmoey
 * Create at 2024/3/15
 */
abstract class SingleProducerSequencerPad extends AbstractSequencer {
    protected long p1, p2, p3, p4, p5, p6, p7;

    SingleProducerSequencerPad(int bufferSize, WaitStrategy waitStrategy) {
        super(bufferSize, waitStrategy);
    }
}

abstract class SingleProducerSequencerFields extends SingleProducerSequencerPad {
    SingleProducerSequencerFields(int bufferSize, WaitStrategy waitStrategy) {
        super(bufferSize, waitStrategy);
    }

    long nextValue = Sequence.INITIAL_VALUE;

    /**
     * 缓存最慢消费者的进度
     */
    long cachedValue = Sequence.INITIAL_VALUE;
}

public final class SingleProducerSequencer extends SingleProducerSequencerFields {

    public SingleProducerSequencer(int bufferSize, WaitStrategy waitStrategy) {
        super(bufferSize, waitStrategy);
    }

    @Override
    public boolean hasAvailableCapacity(int requiredCapacity) {
        return hasAvailableCapacity(requiredCapacity, false);
    }

    private boolean hasAvailableCapacity(int requiredCapacity, boolean doStore) {
        long nextValue = this.nextValue;

        long wrapPoint = (nextValue + requiredCapacity) - bufferSize;

        // 拿到最慢消费者的进度
        long cachedGatingSequence = this.cachedValue;

        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue) {
            if (doStore) {
                cursor.setVolatile(nextValue);
            }
            // 这里就是得到最小的消费者进度
            long minSequence = Util.getMinimumSequence(gatingSequences, nextValue);
            // 把最小的消费者进度缓存一下，因为cachedValue可能不是最新的值，因为消费者进度也不是使用Volatile方式更新的
            // 不保证立即可见性，所以，上面的判断可能是基于旧的消费者进度判断的，发现可能覆盖未被消费的数据后，立刻重新查询一下
            // 最新的当前最慢消费者进度
            this.cachedValue = minSequence;
            //这里再判断一下，看看最新的当前最慢消费者进度是否还小于wrapPoint
            //小于就意味着仍然会覆盖尚未消费的数据
            if (wrapPoint > minSequence) {   //所以返回false即可，表示没有足够的容量分配这么多序号
                return false;
            }
        }
        //走到这里意味着有足够的容量
        return true;
    }

    @Override
    public long next() {
        return next(1);
    }

    @Override
    public long next(int n) {   //判断是否小于1
        if (n < 1) {   //抛出异常
            throw new IllegalArgumentException("n must be > 0");
        }
        // 先得到当前的序号，这个序号就是序号生成器已经分配好了的序号
        // 其实也就可以代表生产者的最新进度
        long nextValue = this.nextValue;
        // 加上要申请的序号的个数，得到最终要申请到的序号的值
        long nextSequence = nextValue + n;
        // 这里的逻辑其实和上面hasAvailableCapacity分析的逻辑一样
        // 都是为了判断是否会覆盖尚未被消费的数据
        long wrapPoint = nextSequence - bufferSize;
        // 得到消费者中最慢的那个消费进度
        long cachedGatingSequence = this.cachedValue;
        // 和之前hasAvailableCapacity分析的逻辑一摸一样，就不再重复注释了
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue) {   //这里有点不一样，如果发现快要覆盖未消费的数据了，就立刻更新一下生产者当前的进度
            // 并且保证内存可见
            cursor.setVolatile(nextValue);
            // 定义一个记录最慢消费进度的局部变量
            long minSequence;
            // 这里就和hasAvailableCapacity方法的逻辑不同了，注意，这里是要真正的分配序号了，如果分配不了，就要让线程等待，直到能够分配为止
            // minSequence = Util.getMinimumSequence(gatingSequences, nextValue)得到的是最新的最慢消费者的进度
            // 所以下面会判断，wrapPoint是不是一直大于最新的最慢消费者进度，如果大于，就不能分配，只有小于的时候，才能分配
            while (wrapPoint > (minSequence = Util.getMinimumSequence(gatingSequences, nextValue))) {
                LockSupport.parkNanos(1L);
            }
            //把最新的最慢消费者进度的缓存更新一下
            this.cachedValue = minSequence;
        }
        //走到这里，说明当前的生产者线程已经不阻塞了，因为在上面循环中的等待，已经让消费者把该消费的数据都消费了
        //所以，这里就可以直接把最新的要分配的序号赋值给nextValue成员变量了，意味着这个最新的值可以分配给生产者使用了
        this.nextValue = nextSequence;
        //返回最新的分配出来的序号，交给生产者使用
        return nextSequence;
    }

    @Override
    public long tryNext() throws InsufficientCapacityException {
        return tryNext(1);
    }

    @Override
    public long tryNext(int n) throws InsufficientCapacityException {
        if (n < 1) {
            throw new IllegalArgumentException("n must be > 0");
        }
        // 这里就直接判断一下，看是否可以申请这么多，这个方法的逻辑上面已经详细分析过了
        if (!hasAvailableCapacity(n, true)) {
            throw InsufficientCapacityException.INSTANCE;
        }
        // 走到这里，说明完全可以申请，所以直接计算出最新的可用序号，然后返回即可

        return this.nextValue += n;
    }

    @Override
    public long remainingCapacity() {
        long nextValue = this.nextValue;
        long consumed = Util.getMinimumSequence(gatingSequences, nextValue);
        return getBufferSize() - (nextValue - consumed);
    }

    @Override
    public void publish(long sequence) {
        cursor.set(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void publish(long lo, long hi) {
        publish(hi);
    }

    @Override
    public void claim(long sequence) {
        this.nextValue = sequence;
    }

    @Override
    public boolean isAvailable(long sequence) {
        return sequence <= cursor.get();
    }

    @Override
    public long getHighestPublishedSequence(long lowerBound, long availableSequence) {
        return availableSequence;
    }
}

