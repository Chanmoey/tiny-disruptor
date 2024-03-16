package com.moon.distuptor;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public interface Cursored {

    /**
     * 获取生产者当前的生产进度
     */
    long getCursor();
}
