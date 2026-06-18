package com.xreatlabs.xdiscordultimate.events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class XDiscordEventBus {

    private static final List<Object> listeners = new CopyOnWriteArrayList<>();

    public static void register(Object listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void unregister(Object listener) {
        listeners.remove(listener);
    }

    public static void publish(Object event) {
        if (event == null) return;

        for (Object listener : listeners) {
            notifyListener(listener, event);
        }
    }

    private static void notifyListener(Object listener, Object event) {
        try {
            java.lang.reflect.Method[] methods = listener.getClass().getDeclaredMethods();
            for (java.lang.reflect.Method method : methods) {
                if (method.getParameterCount() == 1 &&
                    method.getParameterTypes()[0].isInstance(event)) {

                    method.setAccessible(true);
                    method.invoke(listener, event);
                }
            }
        } catch (Exception e) {
        }
    }

    public static void clear() {
        listeners.clear();
    }

    public static int getListenerCount() {
        return listeners.size();
    }
}
