package dev.naominet.listclient.eventBus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventManager {
    public static final EventManager instance = new EventManager();

    private final Map<Class<?>, List<Handler>> handlerMap = new ConcurrentHashMap<>();

    private EventManager() {}

    public void register(Object owner) {
        for (Method method : owner.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(EventTarget.class)) {
                continue;
            }

            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !Event.class.isAssignableFrom(params[0])) {
                continue;
            }

            method.setAccessible(true);
            handlerMap
                .computeIfAbsent(params[0], k -> new ArrayList<>())
                .add(new Handler(owner, method));
        }
    }

    public void unregister(Object owner) {
        for (List<Handler> handlers : handlerMap.values()) {
            handlers.removeIf(h -> h.owner == owner);
        }
    }

    public void call(Event event) {
        List<Handler> handlers = handlerMap.get(event.getClass());
        if (handlers == null || handlers.isEmpty()) {
            return;
        }

        // Iterate a snapshot in case a handler unregisters during dispatch
        for (Handler handler : new ArrayList<>(handlers)) {
            try {
                handler.method.invoke(handler.owner, event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static class Handler {
        final Object owner;
        final Method method;

        Handler(Object owner, Method method) {
            this.owner = owner;
            this.method = method;
        }
    }
}
