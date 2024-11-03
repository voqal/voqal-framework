package com.intellij.openapi.util;

import com.intellij.openapi.Disposable;

public class Disposer {

    // Static method to create a new Disposable
    public static Disposable newDisposable() {
        return new Disposable() {
            @Override
            public void dispose() {
                // No-op implementation
            }
        };
    }

    // Static method to register a child Disposable
    public static void register(Disposable parent, Disposable child) {
        // Implementation here
    }

    // Overloaded static method to register with a lambda
    public static void register(Disposable parent, Runnable exec) {
        // Implementation here
    }

    // Static method to dispose of a Disposable
    public static void dispose(Disposable disposable) {
        // Implementation here
    }
}
