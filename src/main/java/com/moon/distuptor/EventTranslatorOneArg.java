package com.moon.distuptor;

/**
 * 生产者预先生产了一个Event对象，那么这个类用来再设置Event对象中的数据
 * Event是包裹消息的盒子，他里面的成员属性，才是真正的消息本身
 *
 * @author Chanmoey
 * Create at 2024/3/14
 */
public interface EventTranslatorOneArg<T, A>
{
    /**
     * Translate a data representation into fields set in given event
     *
     * @param event    into which the data should be translated.
     * @param sequence that is assigned to event.
     * @param arg0     The first user specified argument to the translator
     */
    void translateTo(T event, long sequence, A arg0);
}
