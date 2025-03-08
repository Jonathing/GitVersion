/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitver.internal;

import net.minecraftforge.gitver.api.GitVersionException;

public final class GitVersionExceptionInternal extends GitVersionException {
    GitVersionExceptionInternal(String message) {
        super(message);
    }

    GitVersionExceptionInternal(String message, Throwable cause) {
        super(message, cause);
    }
}
