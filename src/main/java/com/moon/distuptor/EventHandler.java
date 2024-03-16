package com.moon.distuptor;

/**
 * 消费者接口
 *
 * @author Chanmoey
 * Create at 2024/3/14
 */
public interface EventHandler<T> {
    void onEvent(T event, long sequence, boolean endOfBatch) throws Exception;
}
