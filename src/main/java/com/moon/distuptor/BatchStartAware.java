package com.moon.distuptor;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public interface BatchStartAware {
    // 参数就是这次一共要消费的事件的个数
    void onBatchStart(long batchSize);
}
