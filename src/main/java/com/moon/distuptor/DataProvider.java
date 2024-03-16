package com.moon.distuptor;

/**
 * 该接口为消费者提供数据，因为RingBuffer是生产者使用的
 * 但是消费者也要使用，为了隔离其他方法，通过接口进行隔离
 *
 * @author Chanmoey
 * Create at 2024/3/15
 */
public interface DataProvider<T> {

    T get(long sequence);
}
