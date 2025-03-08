/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitver.api;

import net.minecraftforge.gitver.internal.GitVersionConfigImpl;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.tomlj.Toml;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * The configuration for GitVersion. This is used to determine the version of a project based on the declared
 * subprojects and their respective filters.
 *
 * @see Project
 */
@NotNullByDefault
public sealed interface GitVersionConfig extends Function<@Nullable String, GitVersionConfig.@Nullable Project> permits GitVersionConfigImpl, GitVersionConfigImpl.Empty {
    /**
     * Gets the project at the given path.
     *
     * @param path The path
     * @return The project, or {@code null} if not found
     */
    @Nullable Project getProject(@Nullable String path);

    /** @return The root project */
    @SuppressWarnings("DataFlowIssue")
    private Project getRootProject() {
        return this.getProject("");
    }

    /** @return All projects, including the {@linkplain #getRootProject() root project}. */
    default Collection<Project> getAllProjects() {
        return Collections.singleton(this.getRootProject());
    }

    /**
     * Validates this configuration by ensuring that all declared subprojects exist from the given root.
     *
     * @param root The root to validate from
     * @throws IllegalArgumentException If a subproject path does not exist
     */
    default void validate(@UnknownNullability File root) throws IllegalArgumentException { }

    /** @return Any errors made during the config TOML parsing */
    default List<? extends RuntimeException> errors() {
        return Collections.emptyList();
    }

    /** The default config, used if there is no config available. */
    GitVersionConfig EMPTY = GitVersionConfigImpl.EMPTY;

    /**
     * Attempts to parse the given config file into a {@link GitVersionConfig} using {@linkplain Toml TOMLJ}. If it
     * cannot be parsed or read, the default {@linkplain #EMPTY empty config} is returned. Other errors will still throw
     * an exception, however.
     *
     * @param config The config file
     * @return The parsed config, or the default {@linkplain #EMPTY empty config} if the file does not exist
     */
    static GitVersionConfig parse(@UnknownNullability File config) {
        try {
            return GitVersionConfigImpl.parse(config);
        } catch (IOException e) {
            return EMPTY;
        }
    }

    sealed interface Project permits GitVersionConfigImpl.ProjectImpl {
        String getPath();

        String getTagPrefix();

        String[] getFilters();
    }

    @Override
    default @Nullable Project apply(@Nullable String path) {
        return this.getProject(path);
    }
}
