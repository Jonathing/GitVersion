/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitversion.gradle;

import org.gradle.api.file.Directory;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;

// NOTE: See GitVersion
@NotNullByDefault
public sealed interface GitVersionExtension permits GitVersionExtensionInternal {
    String NAME = "gitversion";


    /* VERSIONING */

    String getTagOffset();

    String getTagOffsetBranch();

    String getTagOffsetBranch(String @UnknownNullability ... allowedBranches);

    String getTagOffsetBranch(@UnknownNullability Collection<String> allowedBranches);

    String getMCTagOffsetBranch(@UnknownNullability String mcVersion);

    default String getMCTagOffsetBranch(Provider<? extends CharSequence> mcVersion) {
        return this.getMCTagOffsetBranch(mcVersion.map(Object::toString).getOrNull());
    }

    default String getMCTagOffsetBranch(ProviderConvertible<? extends CharSequence> mcVersion) {
        return this.getMCTagOffsetBranch(mcVersion.asProvider());
    }

    String getMCTagOffsetBranch(@UnknownNullability String mcVersion, String... allowedBranches);

    default String getMCTagOffsetBranch(Provider<? extends CharSequence> mcVersion, String... allowedBranches) {
        return this.getMCTagOffsetBranch(mcVersion.map(Object::toString).getOrNull(), allowedBranches);
    }

    default String getMCTagOffsetBranch(ProviderConvertible<? extends CharSequence> mcVersion, String... allowedBranches) {
        return this.getMCTagOffsetBranch(mcVersion.asProvider(), allowedBranches);
    }

    String getMCTagOffsetBranch(@UnknownNullability String mcVersion, Collection<String> allowedBranches);

    default String getMCTagOffsetBranch(Provider<? extends CharSequence> mcVersion, Collection<String> allowedBranches) {
        return this.getMCTagOffsetBranch(mcVersion.map(Object::toString).getOrNull(), allowedBranches);
    }

    default String getMCTagOffsetBranch(ProviderConvertible<? extends CharSequence> mcVersion, Collection<String> allowedBranches) {
        return this.getMCTagOffsetBranch(mcVersion.asProvider(), allowedBranches);
    }


    /* INFO */

    Info getInfo();

    @Nullable String getUrl();

    @NotNullByDefault
    sealed interface Info extends Serializable permits GitVersionExtensionInternal.Info {
        String getTag();

        String getOffset();

        String getHash();

        String getBranch();

        String getBranch(boolean versionFriendly);

        String getCommit();

        String getAbbreviatedId();
    }


    /* FILTERING */

    String getTagPrefix();

    @Unmodifiable Collection<String> getFilters();


    /* FILE SYSTEM */

    Directory getGitDir();

    Directory getRoot();

    Directory getProject();

    String getProjectPath();

    default Provider<String> getRelativePath(FileSystemLocationProperty<?> file) {
        return this.getRelativePath(file.getLocationOnly());
    }

    default Provider<String> getRelativePath(Provider<? extends FileSystemLocation> file) {
        return file.map(FileSystemLocation::getAsFile).map(this::getRelativePath);
    }

    default String getRelativePath(FileSystemLocation file) {
        return this.getRelativePath(file.getAsFile());
    }

    String getRelativePath(File file);

    default Provider<String> getRelativePath(boolean fromRoot, FileSystemLocationProperty<?> file) {
        return this.getRelativePath(fromRoot, file.getLocationOnly());
    }

    default Provider<String> getRelativePath(boolean fromRoot, Provider<? extends FileSystemLocation> file) {
        return file.map(f -> this.getRelativePath(fromRoot, f.getAsFile()));
    }

    default String getRelativePath(boolean fromRoot, FileSystemLocation file) {
        return this.getRelativePath(fromRoot, file.getAsFile());
    }

    String getRelativePath(boolean fromRoot, File file);
}
