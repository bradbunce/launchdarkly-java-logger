package io.github.bradbunce.ldlogger;

import org.slf4j.LoggerFactory;

/** Shared backend-detection helpers for the built-in bridges. */
final class Backends {

    private Backends() {
        throw new AssertionError("No instances");
    }

    /**
     * Whether a class is on the classpath, without initializing it.
     *
     * <p>Guarding on this before touching a backend's types is what lets all
     * three built-in bridges coexist in one jar while any of them may be absent
     * at runtime.
     */
    static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, Backends.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * Whether SLF4J is bound to a logger factory from the given package.
     *
     * <p>Matched by package rather than exact class name deliberately: bindings
     * rename their factory class between releases - slf4j-jdk14 ships
     * {@code JDK14LoggerFactory}, not the {@code JULLoggerFactory} its package
     * naming suggests - while the package itself is stable.
     */
    static boolean isBoundToPackage(String packagePrefix) {
        return boundFactoryName().startsWith(packagePrefix);
    }

    /**
     * The class name of the bound SLF4J logger factory, for diagnostics.
     *
     * <p>Never null: SLF4J falls back to a NOP factory when no provider is
     * found, rather than returning null.
     */
    static String boundFactoryName() {
        return LoggerFactory.getILoggerFactory().getClass().getName();
    }
}
