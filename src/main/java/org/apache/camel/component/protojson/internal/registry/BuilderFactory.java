package org.apache.camel.component.protojson.internal.registry;

import com.google.protobuf.Message;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

public final class BuilderFactory {

    private final ConcurrentHashMap<Class<?>, MethodHandle> cache = new ConcurrentHashMap<>();

    public Message.Builder newBuilder(Class<? extends Message> type) {
        try {
            MethodHandle mh = cache.computeIfAbsent(type, this::findNewBuilderHandle);
            return (Message.Builder) mh.invoke();
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to create builder for " + type.getName(), e);
        }
    }

    private MethodHandle findNewBuilderHandle(Class<?> cls) {
        try {
            Method m = cls.getMethod("newBuilder");
            m.setAccessible(true);
            return MethodHandles.lookup().unreflect(m);
        } catch (Exception e) {
            throw new IllegalStateException("No static newBuilder() on " + cls.getName(), e);
        }
    }
}
