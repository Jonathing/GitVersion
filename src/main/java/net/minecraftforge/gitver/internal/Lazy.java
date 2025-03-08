/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitver.internal;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

final class Lazy<T> implements Supplier<T> {
    private final Supplier<T> supplier;
    private @Nullable T value;

    static <T> Lazy<T> of(Supplier<T> supplier) {
        return new Lazy<>(supplier);
    }

    private Lazy(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @Override
    public T get() {
        return this.value != null ? this.value : (this.value = this.supplier.get());
    }
}
