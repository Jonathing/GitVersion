/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitver.api;

import net.minecraftforge.gitver.internal.GitUtils;
import net.minecraftforge.gitver.internal.GitVersionImpl;
import net.minecraftforge.gitver.internal.Util;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * The heart of the GitVersion library. Information about how GitVersion operates can be found on the
 * <a href="https://github.com/MinecraftForge/GitVersion">path page</a>.
 */
@NotNullByDefault
public sealed interface GitVersion extends AutoCloseable permits GitVersionImpl {
    /* BUILDER */

    /**
     * Creates a new builder to build a GitVersion instance. The varying parameters and ephemeral availability warrant
     * this instead of using multiple {@code #of} methods.
     *
     * @return A new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * A builder for creating a {@link GitVersion} instance.
     * <p>
     * At a bare minimum, the root or project directory must be set. If the project directory is not set, it will
     * default to the root directory. If the root directory is not set, it will attempt to find the root directory
     * automatically from the project directory. If the git directory is not set, it will default to {@code .git} in the
     * root directory.
     */
    final class Builder {
        private @Nullable File gitDir;
        private @Nullable File root;
        private @Nullable File project;
        private @Nullable GitVersionConfig config;
        private boolean strict = true;

        private Builder() { }

        /**
         * Sets the git directory for the GitVersion instance. Ideally, this should be located in the root directory.
         *
         * @param gitDir The git directory
         * @return This builder
         */
        public Builder gitDir(File gitDir) {
            this.gitDir = gitDir;
            return this;
        }

        /**
         * Sets the root directory for the GitVersion instance.
         *
         * @param root The root directory
         * @return This builder
         */
        public Builder root(@UnknownNullability File root) {
            this.root = root != null ? root.getAbsoluteFile() : null;
            return this;
        }

        /**
         * Sets the project directory for the GitVersion instance.
         *
         * @param project The project directory
         * @return This builder
         */
        public Builder project(@UnknownNullability File project) {
            this.project = project != null ? project.getAbsoluteFile() : null;
            return this;
        }

        public Builder config(@UnknownNullability File config) {
            this.config = config != null ? GitVersionConfig.parse(config) : null;
            return this;
        }

        /**
         * Sets the config to use for the GitVersion instance.
         *
         * @param config The config
         * @return This builder
         * @see GitVersionConfig#EMPTY
         */
        public Builder config(GitVersionConfig config) {
            this.config = config;
            return this;
        }

        public Builder strict(boolean strict) {
            this.strict = strict;
            return this;
        }

        /**
         * Builds the GitVersion instance.
         *
         * @return The GitVersion instance
         * @throws IllegalArgumentException If neither the root nor project directory is set
         * @throws GitVersionException      If an error occurs during construction
         */
        public GitVersion build() {
            if (this.root == null && this.project == null)
                throw new IllegalArgumentException("Either the root or project directory must be set");

            if (this.root == null)
                this.root = GitUtils.findGitRoot(this.project);

            if (this.project == null)
                this.project = this.root;

            if (this.gitDir == null)
                this.gitDir = new File(this.root, ".git");

            if (this.config == null)
                this.config = GitVersionConfig.parse(new File(this.root, ".gitversion"));

            return new GitVersionImpl(this.gitDir, this.root, this.project, this.config, this.strict);
        }
    }


    /* VERSIONING */

    /**
     * Calculates a version number in the form
     * <code>{@link GitVersionImpl.Info#getTag() tag}.{@link GitVersionImpl.Info#getOffset() offset}</code>.
     * <p>
     * For example, if your current tag is {@code 1.0} and 5 commits have been made since the tagged commit, then the
     * resulting version number will be {@code 1.0.5}.
     *
     * @return The calculated version
     */
    default String getTagOffset() {
        var info = this.getInfo();
        return "%s.%s".formatted(info.getTag(), info.getOffset());
    }

    /** @see #getTagOffsetBranch(Collection) */
    default String getTagOffsetBranch() {
        return this.getTagOffsetBranch(GitUtils.DEFAULT_ALLOWED_BRANCHES);
    }

    /** @see #getTagOffsetBranch(Collection) */
    default String getTagOffsetBranch(@UnknownNullability String... allowedBranches) {
        return this.getTagOffsetBranch(Arrays.asList(Util.ensure(allowedBranches)));
    }

    /**
     * Calculates a version number using {@link #getTagOffset()}. If the current branch is not included in the defined
     * list of allowed branches, it will be appended to the version number. It is important to note that any instances
     * of the forward-slash character ({@code /}) will be replaced with a hyphen ({@code -}).
     * <p>
     * For example, if your current tag is {@code 2.3}, 6 commits have been made since the tagged commit, and you are on
     * a branch named {@literal "feature/cool-stuff"}, then the resulting version number will be
     * {@code 2.3.6-feature-cool-stuff}.
     *
     * @param allowedBranches A list of allowed branches; the current branch is appended if not in this list
     * @return The calculated version
     * @see #getTagOffset()
     */
    default String getTagOffsetBranch(@UnknownNullability Collection<String> allowedBranches) {
        allowedBranches = Util.ensure(allowedBranches);
        var version = this.getTagOffset();
        if (allowedBranches.isEmpty()) return version;

        var branch = this.getInfo().getBranch(true);
        return StringUtils.isEmptyOrNull(branch) || allowedBranches.contains(branch) ? version : "%s-%s".formatted(version, branch);
    }

    /** @see #getMCTagOffsetBranch(String, Collection) */
    default String getMCTagOffsetBranch(@UnknownNullability String mcVersion) {
        if (StringUtils.isEmptyOrNull(mcVersion))
            return this.getTagOffsetBranch();

        var allowedBranches = new ArrayList<>(GitUtils.DEFAULT_ALLOWED_BRANCHES);
        allowedBranches.add(mcVersion);
        allowedBranches.add(mcVersion + ".0");
        allowedBranches.add(mcVersion + ".x");
        allowedBranches.add(mcVersion.substring(0, mcVersion.lastIndexOf('.')) + ".x");

        return this.getMCTagOffsetBranch(mcVersion, allowedBranches);
    }

    /** @see #getMCTagOffsetBranch(String, Collection) */
    default String getMCTagOffsetBranch(String mcVersion, String... allowedBranches) {
        return this.getMCTagOffsetBranch(mcVersion, Arrays.asList(allowedBranches));
    }

    /**
     * Calculates a version number using {@link #getTagOffsetBranch(String...)}, additionally prepending the given
     * Minecraft version.
     * <p>
     * For example, if your current tag is {@code 5.0}, 2 commits have been made since the tagged commit, you are on a
     * branch named {@literal "master"} which is included in the allowed branches, and the given Minecraft version is
     * {@code 1.21.4}, then the resulting version number will be {@code 1.21.4-5.0.2}.
     *
     * @param mcVersion       The minecraft version
     * @param allowedBranches A list of allowed branches
     * @return The calculated version
     */
    default String getMCTagOffsetBranch(String mcVersion, Collection<String> allowedBranches) {
        return "%s-%s".formatted(mcVersion, this.getTagOffsetBranch(allowedBranches));
    }


    /* CHANGELOG */

    /**
     * Attempts to generate a changelog using the parameters given to this GitVersion.
     * <p>
     * If {@linkplain Builder#strict(boolean) strict mode} is disabled, this method will return an empty string instead
     * of throwing an exception.
     *
     * @param start     The tag or commit hash to start the changelog from, or {@code null} to start from the current
     * @param url       The URL to the repository, or {@code null} to attempt to use the
     *                  {@linkplain Info#getUrl() auto-calculated URL} (if available)
     * @param plainText Whether to generate the changelog in plain text ({@code} false to use Markdown formatting)
     * @return The generated changelog
     * @throws GitVersionException If changelog fails to generate (in {@linkplain Builder#strict(boolean) strict mode})
     */
    String generateChangelog(@Nullable String start, @Nullable String url, boolean plainText) throws GitVersionException;


    /* INFO */

    /**
     * Gets {@link Info info} of the current state of the Git repository. This is used to generate the version string,
     * and is exposed here to allow access to other parts of the version (or general) information.
     * <p>
     * This object is lazily recalculated whenever one of the values of this version object change, such as the tag
     * prefix or match filters. As such, it is recommended not to cache the result of this method.
     *
     * @return Information about the current state of the Git repository
     * @see Info
     */
    Info getInfo() throws GitVersionException;

    /**
     * Represents information about a git repository. This can be used to access other information when the standard
     * versioning methods in {@link GitVersion} do not suffice.
     */
    @NotNullByDefault
    sealed interface Info extends Serializable permits GitVersionImpl.Info {
        /** @return The current tag as described by the Git repository using the applied filters */
        String getTag();

        /**
         * @return The commit offset from the {@linkplain #getTag() current tag} to {@code HEAD} using the applied
         * filters
         */
        String getOffset();

        /** @return The hash as described by the Git repository */
        String getHash();

        /**
         * @return The current branch
         * @see #getBranch(boolean)
         */
        String getBranch();

        /**
         * Gets the current branch, optionally formatted to be more version string friendly. This is done by replacing
         * any forward-slashes ({@code /}) with hyphens ({@code -}). If the branch is a pull request branch, it is
         * formatted as {@code pr} followed by the pull request number.
         *
         * @param versionFriendly Whether to format the branch to make it version string friendly
         * @return The current branch
         */
        default String getBranch(boolean versionFriendly) {
            var branch = this.getBranch();
            if (!versionFriendly || branch.isBlank()) return branch;

            if (branch.startsWith("pulls/"))
                branch = "pr" + branch.substring(branch.lastIndexOf('/') + 1);
            return branch.replaceAll("[\\\\/]", "-");
        }

        /**
         * @return The long {@code HEAD} commit hash
         * @see #getAbbreviatedId()
         */
        String getCommit();

        /**
         * @return The short {@code HEAD} commit hash
         * @see #getCommit()
         */
        String getAbbreviatedId();

        /** @return The path URL, or {@code null} if there is none */
        @Nullable String getUrl();
    }


    /* FILTERING */

    /** @return The tag prefix used when filtering tags */
    String getTagPrefix();

    /** @return The filters used when filtering tags (excluding the {@linkplain #getTagPrefix() tag prefix}) */
    @UnmodifiableView Collection<String> getFilters();


    /* FILE SYSTEM */

    /** @return The {@code .git} directory containing the Git repository */
    File getGitDir();

    /** @return The root directory of the Git repository, ideally containing the {@linkplain #getGitDir() git directory} */
    File getRoot();

    /** @return The path directory, which may or may not also be the {@linkplain #getRoot() root} */
    File getProject();

    /**
     * Gets the relative {@linkplain #getProject() path} path string. This could be an empty string if the path
     * directory is the same as the {@linkplain #getRoot() root}.
     * <p>
     * This path string is calculated using the
     * {@linkplain java.nio.file.Path#relativize(java.nio.file.Path) relative path} from the root.
     *
     * @return The relative path string
     */
    default String getProjectPath() {
        return GitUtils.getRelativePath(this.getRoot(), this.getProject());
    }

    default String getRelativePath(File file) {
        return this.getRelativePath(false, file);
    }

    /**
     * Gets the relative path string of a given file.
     * <p>
     * This path string is calculated using the
     * {@linkplain java.nio.file.Path#relativize(java.nio.file.Path) relative path} from either the
     * {@linkplain #getRoot() root} or the {@linkplain #getProject() path}, depending on the {@code fromRoot} boolean
     * parameter.
     *
     * @param fromRoot Whether to get the relative path from the root ({@code true}) or the path ({@code false})
     * @param file     The file to get the relative path to
     * @return The relative path string
     */
    default String getRelativePath(boolean fromRoot, File file) {
        return GitUtils.getRelativePath(fromRoot ? this.getRoot() : this.getProject(), file);
    }

    /**
     * Attempts to get the relative path of the given file from the root of its Git repository. This is exposed
     * primarily to allow Gradle plugins to get the relative path without needing to declare the path directory
     * directly, as it can cause issues with task configuration.
     *
     * @param file The file to find the relative path to from the root of its Git repository
     * @return The relative path, or an empty string if the file is not in (or itself is) a Git repository
     * @see #getRelativePath(boolean, File)
     * @deprecated Will be removed once GitVersion has its own Gradle plugin and extension, instead of being a part of
     * <a href="https://github.com/MinecraftForge/GradleUtils">GradleUtils</a>
     */
    @Deprecated(forRemoval = true)
    static String findRelativePath(File file) {
        return GitUtils.getRelativePath(GitUtils.findGitRoot(file), file);
    }


    /* SUBPROJECTS */

    /** @return The declared subprojects of this path. */
    @UnmodifiableView Collection<File> getSubprojects();

    /**
     * Gets the relative subproject path strings from the declared subprojects. The path strings are relative from the
     * {@linkplain #getProject() project} directory. Use {@link #getSubprojectPaths(boolean)} to conditionally get the
     * path strings relative from the {@linkplain #getRoot() root} instead.
     * <p>
     * These path strings are calculated using the
     * {@linkplain java.nio.file.Path#relativize(java.nio.file.Path) relative path} from the
     * {@linkplain #getRoot() root} to each subproject.
     *
     * @return The subproject paths
     * @see #getSubprojects()
     * @see #getSubprojectPaths(boolean)
     */
    default Collection<String> getSubprojectPaths() {
        return this.getSubprojectPaths(false);
    }

    /**
     * Gets the relative subproject path strings from the declared subprojects. The path strings are relative from
     * either the {@linkplain #getRoot() root} or {@linkplain #getProject() project} directory, depending on the
     * {@code fromRoot} boolean parameter.
     * <p>
     * These path strings are calculated using the
     * {@linkplain java.nio.file.Path#relativize(java.nio.file.Path) relative path} from the
     * {@linkplain #getRoot() root} to each subproject.
     *
     * @return The subproject paths
     * @see #getSubprojects()
     */
    default Collection<String> getSubprojectPaths(boolean fromRoot) {
        return this.getSubprojects()
                   .stream()
                   .map(dir -> GitUtils.getRelativePath(fromRoot ? this.getRoot() : this.getProject(), dir))
                   .filter(s -> !s.isBlank())
                   .collect(Collectors.toCollection(ArrayList::new));
    }


    /* REPOSITORY */

    /** Closes this GitVersion instance along with its Git repository (if it was opened). */
    @Override
    void close();


    /* EXPERIMENTAL */

    /**
     * Prevents JGit's {@link SystemReader} from
     * {@linkplain SystemReader#openSystemConfig(Config, FS) reading the system configuration file}.
     * <p>
     * This is a <strong>potentially very destructive action</strong> since it replaces the global system reader used
     * for all JGit operations. It should not be used in production, with the exception of the Gradle environment with
     * configuration cache. This is because reading the system configuration file requires executing the System's
     * {@code git} command, which is not allowed when using Gradle configuration cache.
     * <p>
     * A preferable alternative to using this method, if applicable, is to set the
     * {@link org.eclipse.jgit.lib.Constants#GIT_CONFIG_NOSYSTEM_KEY <code>"GIT_CONFIG_NOSYSTEM"</code>} environment
     * variable to {@code "true"}.
     *
     * @return The original system reader before delegating it to disable reading the system configuration file. Use
     * this if you plan on restoring the reader later
     * @apiNote Under no circumstances should this method be invoked in a non-Gradle production environment. If you do,
     * it is at your own risk.
     */
    @ApiStatus.Experimental
    static SystemReader disableSystemConfig() {
        var reader = SystemReader.getInstance();
        SystemReader.setInstance(new SystemReader.Delegate(reader) {
            @Override
            public FileBasedConfig openSystemConfig(Config parent, FS fs) {
                return new FileBasedConfig(parent, null, fs) {
                    @Override
                    public void load() { }

                    @Override
                    public boolean isOutdated() {
                        return false;
                    }
                };
            }
        });
        return reader;
    }
}
