/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitversion.gradle;

import org.gradle.api.Project;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@NotNullByDefault
non-sealed interface GitVersionExtensionInternal extends GitVersionExtension, HasPublicType {
    List<String> DEFAULT_ALLOWED_BRANCHES = List.of("master", "main", "HEAD");

    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(GitVersionExtension.class);
    }

    void attachTo(Project project);


    /* VERSIONING */

    @Override
    default String getTagOffset() {
        var info = this.getInfo();
        return "%s.%s".formatted(info.getTag(), info.getOffset());
    }

    @Override
    default String getTagOffsetBranch() {
        return this.getTagOffsetBranch(DEFAULT_ALLOWED_BRANCHES);
    }

    @Override
    default String getTagOffsetBranch(String @UnknownNullability ... allowedBranches) {
        return this.getTagOffsetBranch(Arrays.asList(allowedBranches != null ? allowedBranches : new String[0]));
    }

    @Override
    default String getTagOffsetBranch(@UnknownNullability Collection<String> allowedBranches) {
        var version = this.getTagOffset();
        if (allowedBranches == null || allowedBranches.isEmpty()) return version;

        var branch = this.getInfo().getBranch(true);
        return branch.isBlank() || allowedBranches.contains(branch) ? version : "%s-%s".formatted(version, branch);
    }

    @Override
    default String getMCTagOffsetBranch(@UnknownNullability String mcVersion) {
        if (mcVersion == null || mcVersion.isBlank())
            return this.getTagOffsetBranch();

        var allowedBranches = new ArrayList<>(DEFAULT_ALLOWED_BRANCHES);
        allowedBranches.add(mcVersion);
        allowedBranches.add(mcVersion + ".0");
        allowedBranches.add(mcVersion + ".x");
        allowedBranches.add(mcVersion.substring(0, mcVersion.lastIndexOf('.')) + ".x");

        return this.getMCTagOffsetBranch(mcVersion, allowedBranches);
    }

    @Override
    default String getMCTagOffsetBranch(@UnknownNullability String mcVersion, String... allowedBranches) {
        return this.getMCTagOffsetBranch(mcVersion, Arrays.asList(allowedBranches));
    }

    @Override
    default String getMCTagOffsetBranch(@UnknownNullability String mcVersion, Collection<String> allowedBranches) {
        if (mcVersion == null || mcVersion.isBlank())
            return this.getTagOffsetBranch();

        return "%s-%s".formatted(mcVersion, this.getTagOffsetBranch(allowedBranches));
    }


    /* INFO */

    @NotNullByDefault
    record Info(
        String getTag,
        String getOffset,
        String getHash,
        String getBranch,
        String getCommit,
        String getAbbreviatedId
    ) implements GitVersionExtension.Info {
        static final Info EMPTY = new Info("0.0", "0", "00000000", "master", "0000000000000000000000", "00000000");

        @Override
        public String getBranch(boolean versionFriendly) {
            var branch = this.getBranch();
            if (!versionFriendly || branch.isBlank()) return branch;

            if (branch.startsWith("pulls/"))
                branch = "pr" + branch.substring(branch.lastIndexOf('/') + 1);
            return branch.replaceAll("[\\\\/]", "-");
        }
    }


    /* FILE SYSTEM */

    private static String getRelativePath(File root, File file) {
        if (root.equals(file)) return "";
        return getRelativePath(root.toPath(), file.toPath());
    }

    private static String getRelativePath(Path root, Path path) {
        return root.relativize(path).toString().replace(root.getFileSystem().getSeparator(), "/");
    }

    @Override
    default String getProjectPath() {
        return getRelativePath(this.getRoot().getAsFile(), this.getProject().getAsFile());
    }

    @Override
    default String getRelativePath(File file) {
        return this.getRelativePath(false, file);
    }

    @Override
    default String getRelativePath(boolean fromRoot, File file) {
        return getRelativePath(fromRoot ? this.getRoot().getAsFile() : this.getProject().getAsFile(), file);
    }


    /* SERIALIZATION */

    record Output(
        Info info,
        @Nullable String url,

        @Nullable String gitDirPath,
        @Nullable String rootPath,
        @Nullable String projectPath,

        @Nullable String tagPrefix,
        List<String> filters,
        List<String> subprojectPaths
    ) implements Serializable {
        static final Output EMPTY = new Output(
            Info.EMPTY,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of());
    }
}
