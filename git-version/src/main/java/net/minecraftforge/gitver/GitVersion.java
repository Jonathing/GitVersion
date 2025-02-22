package net.minecraftforge.gitver;

import net.minecraftforge.util.git.GitUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Supplier;

public class GitVersion {
    public final File root;
    public final File gitDir;
    public final File project;
    private final Map<File, String> subprojects = new HashMap<>();
    public final String localPath;
    public String tagPrefix;
    public String[] matchFilters;
    private Supplier<GitInfo> infoSupplier;
    private GitInfo info;

    public GitVersion(@Nullable File root, File project, String[] markerFile, String[] ignoreFile, String[] ignoreDir) {
        this(root, project, markerFile, ignoreFile, ignoreDir, null);
    }

    public GitVersion(@Nullable File root, File project, String[] markerFile, String[] ignoreFile, String[] ignoreDir, String tagPrefix) {
        this(root, project, markerFile, ignoreFile, ignoreDir, tagPrefix, new String[0]);
    }

    public GitVersion(@Nullable File root, File project, String[] markerFile, String[] ignoreFile, String[] ignoreDir, String tagPrefix, String... matchFilters) {
        this.root = root != null ? root : GitUtils.findGitRoot(project);
        this.gitDir = new File(this.root, ".git");
        this.project = project;
        if (this.project.compareTo(this.root) < 0)
            throw new IllegalArgumentException("Project directory must be a subdirectory of the root directory!");

        this.localPath = this.getLocalPath();
        this.tagPrefix = sanatizeTagPrefix(tagPrefix != null ? tagPrefix : this.getLocalPath());
        this.matchFilters = matchFilters != null ? matchFilters : new String[0];
        this.findSubprojects(Util.make(new HashSet<>(), markerFile), Util.make(new HashSet<>(), ignoreFile), Util.make(new HashSet<>(), ignoreDir));
        this.infoSupplier = () -> this.getGitInfo(this::getSubprojectCommitCount);
    }

    private static String sanatizeTagPrefix(@Nullable String tagPrefix) {
        if (StringUtils.isEmptyOrNull(tagPrefix)) return "";

        return tagPrefix.endsWith("-") ? tagPrefix : tagPrefix + "-";
    }

    public void setTagPrefix(@Nullable String tagPrefix) {
        this.tagPrefix = sanatizeTagPrefix(tagPrefix);
        this.infoSupplier = () -> this.getGitInfo(this::getSubprojectCommitCount);
        this.info = null;
    }

    public void setMatchFilters(String[] matchFilters) {
        this.matchFilters = matchFilters != null ? matchFilters : new String[0];
        this.infoSupplier = () -> this.getGitInfo(this::getSubprojectCommitCount);
        this.info = null;
    }

    public GitInfo getInfo() {
        return this.info != null ? this.info : (this.info = this.infoSupplier.get());
    }

    public String getLocalPath() {
        return this.getLocalPath(this.project);
    }

    public String getLocalPath(File subProject) {
        return getLocalPath(this.root, subProject);
    }

    public static String getLocalPath(File root, File subProject) {
        if (root.equals(subProject)) return "";

        var result = subProject.getAbsolutePath().substring(root.getAbsolutePath().length());
        if (result.startsWith(File.separator))
            result = result.substring(1);
        return result;
    }

    private void findSubprojects(Collection<String> markerFile, Collection<String> ignoreFile, Collection<String> ignoreDir) {
        FilenameFilter fileFilter = (dir, name) ->
            !dir.equals(this.project) // directory is not the project root
                && markerFile.contains(name) // marker file is present (typically build.gradle)
                && FileUtils.listFiles(dir, null, false).stream().map(File::getName).noneMatch(ignoreFile::contains); // ignore file is not present

        IOFileFilter dirFilter = DirectoryFileFilter.INSTANCE;
        for (var dir : ignoreDir)
            dirFilter = FileFilterUtils.and(FileFilterUtils.asFileFilter(f -> !dir.equals(this.getLocalPath(f))));

        var files = FileUtils.listFiles(this.project, FileFilterUtils.asFileFilter(fileFilter), dirFilter);

        for (var file : files) {
            var subproject = file.getParentFile();
            this.subprojects.put(subproject, this.getLocalPath(subproject));
        }
    }

    public Collection<String> getSubprojectPaths() {
        return this.subprojects.values();
    }

    private GitInfo getGitInfo(CommitCountProvider commitCountProvider) {
        var reader = GitUtils.disableSystemConfig();
        SystemReader.setInstance(reader);

        try (Git git = Git.open(this.gitDir)) {
            var describedTag = Util.make(git.describe(), it -> {
                it.setTags(true);
                it.setLong(true);

                try {
                    if (!this.tagPrefix.isEmpty())
                        it.setMatch(this.tagPrefix + "**");
                    else
                        Util.setExclude(it, "*-*");

                    if (this.matchFilters.length != 0)
                        it.setMatch(this.matchFilters);
                } catch (Exception e) {
                    Util.sneak(e);
                }
            }).call();

            var desc = Util.rsplit(describedTag, "-", 2);
            if (desc == null) {
                System.err.printf("ERROR: Failed to describe git info! Incorrect filters? Tag prefix: %s, glob filters: %s%n", this.tagPrefix, String.join(", ", this.matchFilters));
                return GitInfo.EMPTY;
            }

            Ref head = git.getRepository().exactRef(Constants.HEAD);
            var longBranch = Util.make(() -> {
                if (!head.isSymbolic()) return null;

                var target = head.getTarget();
                return target != null ? target.getName() : null;
            }); // matches Repository.getFullBranch() but returning null when on a detached HEAD

            var ret = new GitInfo.Builder();
            var tag = Util.make(() -> {
                var t = desc.get(0).substring(this.tagPrefix.length());
                return t.startsWith("v") && t.length() > 1 && Character.isDigit(t.charAt(1))
                    ? t.substring(1)
                    : t;
            });
            ret.tag = tag;

            ret.offset = commitCountProvider.getAsString(git, tag, () -> desc.get(1));
            ret.hash = desc.get(2);
            if (longBranch != null) ret.branch = Repository.shortenRefName(longBranch);
            ret.commit = ObjectId.toString(head.getObjectId());
            ret.abbreviatedId = head.getObjectId().abbreviate(8).name();

            // Remove any lingering null values
            return ret.build();
        } catch (Exception e) {
            e.printStackTrace();
            return GitInfo.EMPTY;
        } finally {
            SystemReader.setInstance(reader.parent);
        }
    }

    protected final int getSubprojectCommitCount(Git git, String tag) {
        var filter = this.getLocalPath();
        var subprojectFilters = Util.ensure(this.subprojects.values());
        if (StringUtils.isEmptyOrNull(filter) && subprojectFilters.isEmpty()) return -1;

        try {
            //        println "Getting subproject commit count! Tag: $tag, filter: $filter"
            var tags = GitUtils.getTagToCommitMap(git);
            var commitHash = tags.get(tag);
            var commit = commitHash != null ? ObjectId.fromString(commitHash) : GitUtils.getFirstCommitInRepository(git).toObjectId();

            var start = GitUtils.getCommitFromId(git, commit);
            var end = GitUtils.getHead(git);

            var log = git.log().add(end);

            // If our starting commit contains at least one parent (it is not the 'root' commit), exclude all of those parents
            for (var parent : start.getParents())
                log.not(parent);
            // We do not exclude the starting commit itself, so the commit is present in the returned iterable

            if (!StringUtils.isEmptyOrNull(filter))
                log.addPath(filter);

            for (var subproject : subprojectFilters)
                log.excludePath(subproject);

            var count = Util.count(log.call());
            if (count < 0) {
                System.err.printf("WARNING: Failed to count commits for tag %s!%n", tag);
            }
            return count;
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}
