/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitver.internal;

import net.minecraftforge.gitver.api.GitVersionConfig;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;
import org.tomlj.TomlVersion;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@NotNullByDefault
public record GitVersionConfigImpl(
    Map<String, Project> projects,
    @Override List<? extends RuntimeException> errors
) implements GitVersionConfig {
    public static final GitVersionConfig EMPTY = new Empty();

    @Override
    public @Nullable Project getProject(@Nullable String path) {
        return this.projects.get(path);
    }

    @Override
    public Collection<Project> getAllProjects() {
        return this.projects.values();
    }

    @Override
    public void validate(File root) {
        for (var path : this.projects.keySet()) {
            if (!path.isEmpty() && !new File(root, path).exists())
                throw new IllegalArgumentException("Subproject path '%s' does not exist. Specify it explicitly in the config if necessary.".formatted(path));
        }
    }

    public static GitVersionConfig parse(@UnknownNullability File config) throws IOException {
        if (config == null || !config.exists()) return EMPTY;

        var toml = Toml.parse(config.toPath(), TomlVersion.V1_0_0);
        var keys = toml.keySet();
        if (keys.contains(""))
            throw new IllegalArgumentException("Config file cannot have a table with an empty string ([\"\"]), use [root] instead.");

        var projects = new HashMap<String, GitVersionConfig.Project>();
        projects.put("", ProjectImpl.parse(toml));
        for (var key : keys) {
            if ("root".equals(key) || !toml.isTable(key)) continue;

            //noinspection DataFlowIssue - checked by TomlTable#isTable
            var project = ProjectImpl.parse(key, toml.getTable(key));
            projects.put(project.getPath, project);
        }

        return new GitVersionConfigImpl(projects, toml.errors());
    }

    public record ProjectImpl(
        @Override String getPath,
        @Override String getTagPrefix,
        @Override String[] getFilters
    ) implements GitVersionConfig.Project {
        private static final ProjectImpl DEFAULT_ROOT = new ProjectImpl("", "", new String[0]);

        private static ProjectImpl parse(TomlParseResult toml) {
            var root = toml.getTableOrEmpty("root");
            if (root.isEmpty()) return DEFAULT_ROOT;

            var tagPrefix = root.contains("tag") ? root.getString("tag") : "";
            var filters = root.getArrayOrEmpty("filters").toList().stream().map(String.class::cast).filter(s -> !s.isBlank()).toArray(String[]::new);
            //noinspection DataFlowIssue - TomlTable#getString checked by #contains
            return new ProjectImpl("", tagPrefix, filters);
        }

        private static ProjectImpl parse(String key, TomlTable table) {
            var project = Objects.requireNonNullElse(table.getString("path"), key);
            var tagPrefix = table.contains("tag") ? table.getString("tag") : project.replace("/", "-");
            var filters = table.getArrayOrEmpty("filters").toList().stream().map(String.class::cast).filter(s -> !s.isBlank()).toArray(String[]::new);
            //noinspection DataFlowIssue - TomlTable#getString checked by #contains
            return new ProjectImpl(project, tagPrefix, filters);
        }

        @Override
        public String toString() {
            return "GitVersionConfig.Project{path=%s, tagPrefix=%s, filters=[%s]}".formatted(this.getPath, this.getTagPrefix, String.join(", ", this.getFilters));
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof ProjectImpl p && Objects.equals(this.getPath, p.getPath);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.getPath);
        }
    }

    public static final class Empty implements GitVersionConfig {
        private Empty() { }

        @Override
        public @Nullable Project getProject(@Nullable String path) {
            return path != null && path.isEmpty() ? ProjectImpl.DEFAULT_ROOT : null;
        }
    }
}
