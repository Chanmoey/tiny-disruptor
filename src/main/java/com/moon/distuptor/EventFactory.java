package com.moon.distuptor;

/**
 * 给RingBuffer使用的，用来预填充RingBuffer
 *
 * @author Chanmoey
 * Create at 2024/3/14
 */
public interface EventFactory<T> {
    T newInstance();
}
