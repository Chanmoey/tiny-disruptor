package com.moon.distuptor;

/**
 * @author Chanmoey
 * Create at 2024/3/16
 */
public interface LifecycleAware {

    void onStart();


    void onShutdown();
}
