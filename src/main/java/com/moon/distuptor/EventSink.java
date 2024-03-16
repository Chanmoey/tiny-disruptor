package com.moon.distuptor;

/**
 * 生产者发布数据的方法
 *
 * @author Chanmoey
 * Create at 2024/3/16
 */
public interface EventSink<E> {

    void publishEvent(EventTranslator<E> translator);

    <A> void publishEvent(EventTranslatorOneArg<E, A> translator, A arg0);
}
