/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitver.internal;

import net.minecraftforge.gitver.api.GitVersionException;
import org.eclipse.jgit.api.Git;

/**
 * A provider for the commit count of a given tag. This is done in GitVersion in
 * {@link GitVersionImpl#getSubprojectCommitCount(Git, String)} by using
 * {@link GitUtils#countCommits(Git, String, Iterable, Iterable) GitUtils.countCommits(Git, String, Iterable,
 * Iterable)}.
 */
@SuppressWarnings("JavadocReference")
@FunctionalInterface
interface CommitCountProvider {
    /**
     * Gets the commit count of the given tag.
     *
     * @param git The Git repository to count commits in
     * @param tag The tag to count commits from
     * @return The commit count
     */
    int get(Git git, String tag) throws GitVersionException;

    /**
     * Gets the commit count of the given tag as a string. If the commit count cannot be retrieved or is {@code -1}, the
     * fallback value is used instead.
     *
     * @param git      The Git repository to count commits in
     * @param tag      The tag to count commits from
     * @param fallback The fallback value to use if the commit count cannot be retrieved
     * @return The commit count as a string, or the fallback value
     * @see #get(Git, String)
     */
    default String getAsString(Git git, String tag, String fallback, boolean strict) throws GitVersionException {
        try {
            int result = this.get(git, tag);
            return result > 0 ? Integer.toString(result) : fallback;
        } catch (GitVersionException e) {
            if (strict) throw e;

            return fallback;
        }
    }
}
