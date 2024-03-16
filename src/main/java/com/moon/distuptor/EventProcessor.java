package com.moon.distuptor;

/**
 * 时间处理器
 *
 * @author Chanmoey
 * Create at 2024/3/14
 */
public interface EventProcessor extends Runnable {
    Sequence getSequence();

    void halt();

    boolean isRunning();
}
