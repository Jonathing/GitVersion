/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitver.api;

import net.minecraftforge.gitver.internal.GitVersionExceptionInternal;

/**
 * An exception that is thrown when an error occurs during any sort of processing when using {@link GitVersion}.
 * <p>
 * If for whatever reason you need to globally suppress these exceptions and use default values instead, you can disable
 * {@linkplain GitVersion.Builder#strict(boolean) strict mode} on GitVersion.
 */
public sealed class GitVersionException extends RuntimeException permits GitVersionExceptionInternal {
    protected GitVersionException(String message) {
        super(message);
    }

    protected GitVersionException(String message, Throwable cause) {
        super(message, cause);
    }
}
