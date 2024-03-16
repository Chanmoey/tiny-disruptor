package com.moon.distuptor;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public interface TimeoutHandler {
    void onTimeout(long sequence) throws Exception;
}