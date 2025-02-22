package net.minecraftforge.util.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GitUtils {
    public static File findGitRoot(File working) {
        for (var dir = working; dir != null; dir = dir.getParentFile())
            if (isGitRoot(dir)) return dir;

        return working;
    }

    public static boolean isGitRoot(File dir) {
        return new File(dir, ".git").exists();
    }

    /**
     * Finds the youngest merge base commit on the current branch.
     *
     * @param git The git workspace to find the merge base in.
     * @return The merge base commit or null.
     */
    public static RevCommit getHead(final Git git) throws IOException {
        var headId = git.getRepository().resolve(Constants.HEAD);
        return getCommitFromId(git, headId);
    }

    /**
     * Determines the commit that the given ref references.
     *
     * @param git   The git workspace to get the commit from.
     * @param other The reference to get the commit for.
     * @return The commit referenced by the given reference in the given git workspace.
     */
    public static RevCommit getCommitFromRef(final Git git, final Ref other) throws IOException {
        return getCommitFromId(git, other.getObjectId());
    }

    /**
     * Determines the commit that the given object references.
     *
     * @param git   The git workspace to get the commit from.
     * @param other The object to get the commit for.
     * @return The commit referenced by the given object in the given git workspace.
     */
    public static RevCommit getCommitFromId(final Git git, final ObjectId other) throws IOException {
        try (RevWalk revWalk = new RevWalk(git.getRepository())) {
            return revWalk.parseCommit(other);
        }
    }

    /**
     * Gets the commit message from the start commit to the end.
     * <p>
     * Returns it in youngest to oldest order (so from end to start).
     *
     * @param git    The git workspace to get the commits from.
     * @param start  The start commit (the oldest).
     * @param end    The end commit (the youngest).
     * @param filter The filter to decide how to ignore certain commits.
     * @return The commit log.
     */
    public static Iterable<RevCommit> getCommitLogFromTo(final Git git, final RevCommit start, final RevCommit end, final String filter) throws GitAPIException, IOException {
        var log = git.log().add(end);

        // If our starting commit contains at least one parent (it is not the 'root' commit), exclude all of those parents
        for (var parent : start.getParents()) {
            log.not(parent);
        }
        // We do not exclude the starting commit itself, so the commit is present in the returned iterable

        if (!StringUtils.isEmptyOrNull(filter))
            log.addPath(filter);

        return log.call();
    }

    /**
     * Builds a map of commit hashes to tag names.
     *
     * @param git The git workspace to get the tags from.
     * @return The commit hashes to tag map.
     */
    public static Map<String, String> getCommitToTagMap(Git git) throws GitAPIException, IOException {
        return getCommitToTagMap(git, null);
    }

    /**
     * Builds a map of commit hashes to tag names.
     *
     * @param git The git workspace to get the tags from.
     * @return The commit hashes to tag map.
     */
    public static Map<String, String> getCommitToTagMap(Git git, @Nullable String tagPrefix) throws GitAPIException, IOException {
        final Map<String, String> versionMap = new HashMap<>();
        for (Ref tag : git.tagList().call()) {
            ObjectId tagId = git.getRepository().getRefDatabase().peel(tag).getPeeledObjectId();
            if (tagId == null) tagId = tag.getObjectId();
            if (!StringUtils.isEmptyOrNull(tagPrefix) && !tagId.name().startsWith(tagPrefix)) continue;

            versionMap.put(tagId.name(), tag.getName().replace(Constants.R_TAGS, ""));
        }

        return versionMap;
    }

    /**
     * Builds a map of tag name to commit hash.
     *
     * @param git The git workspace to get the tags from.
     * @return The tags to commit hash map.
     */
    public static Map<String, String> getTagToCommitMap(Git git) throws GitAPIException, IOException {
        return getTagToCommitMap(git, null);
    }

    /**
     * Builds a map of tag name to commit hash.
     *
     * @param git The git workspace to get the tags from.
     * @return The tags to commit hash map.
     */
    public static Map<String, String> getTagToCommitMap(Git git, @Nullable String tagPrefix) throws GitAPIException, IOException {
        final Map<String, String> versionMap = new HashMap<>();
        for (Ref tag : git.tagList().call()) {
            ObjectId tagId = git.getRepository().getRefDatabase().peel(tag).getPeeledObjectId();
            if (tagId == null) tagId = tag.getObjectId();
            if (!StringUtils.isEmptyOrNull(tagPrefix) && !tagId.name().startsWith(tagPrefix)) continue;

            versionMap.put(tag.getName().replace(Constants.R_TAGS, ""), tagId.name());
        }

        return versionMap;
    }

    public static @Nullable RevCommit getFirstCommitInRepository(final Git git) throws GitAPIException {
        var commits = git.log().call().iterator();

        RevCommit commit = null;
        while (commits.hasNext()) {
            commit = commits.next();
        }

        return commit;
    }

    /**
     * Finds the youngest merge base commit on the current branch.
     *
     * @param git The git workspace to find the merge base in.
     * @return The merge base commit or null.
     */
    public static RevCommit getMergeBaseCommit(final Git git) throws GitAPIException, IOException {
        var headCommit = getHead(git);
        var remoteBranches = getAvailableRemoteBranches(git);
        return remoteBranches
            .stream()
            .filter(branch -> !branch.getObjectId().getName().equals(headCommit.toObjectId().getName()))
            .map(branch -> getMergeBase(git, branch))
            .filter(revCommit -> (revCommit != null) &&
                (!revCommit.toObjectId().getName().equals(headCommit.toObjectId().getName())))
            .min(Comparator.comparing(revCommit -> Integer.MAX_VALUE - revCommit.getCommitTime()))
            .orElse(null);
    }

    /**
     * Get all available remote branches in the git workspace.
     *
     * @param git The git workspace to get the branches from.
     * @return A list of remote branches.
     */
    public static List<Ref> getAvailableRemoteBranches(final Git git) throws GitAPIException {
        return git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
    }

    /**
     * Get the merge base commit between the current and the given branch.
     *
     * @param git   The git workspace to get the merge base in.
     * @param other The other branch to find the merge base with.
     * @return A merge base commit or null.
     */
    public static RevCommit getMergeBase(final Git git, final Ref other) {
        try (RevWalk walk = new RevWalk(git.getRepository())) {
            walk.setRevFilter(RevFilter.MERGE_BASE);
            walk.markStart(getCommitFromRef(git, other));
            walk.markStart(getHead(git));

            RevCommit mergeBase = null;
            RevCommit current;
            while ((current = walk.next()) != null) {
                mergeBase = current;
            }
            return mergeBase;
        } catch (MissingObjectException ignored) {
            return null;
        } catch (IOException e) {
            return Util.sneak(e);
        }
    }

    /**
     * Builds a commit hash to version map. The commits version is build based on forges common version scheme.
     * <p>
     * From the current identifiable-version (in the form of major.minor) a patch section is appended based on the
     * amount of commits since the last tagged commit. A tagged commit get 0 patch section appended. Any commits that
     * are before the first tagged commit will not get a patch section append but a '-pre-' section will be appended,
     * with a commit count as well.
     *
     * @param commits              The commits to build the version map for.
     * @param commitHashToVersions A commit hash to identifiable-version name map.
     * @return The commit hash to calculated version map.
     */
    public static Map<String, String> buildVersionMap(final List<RevCommit> commits, final Map<String, String> commitHashToVersions) {
        //Determine the version that sets the first fixed version commit.
        var prereleaseTargetVersion = getFirstReleasedVersion(commits, commitHashToVersions);
        //Inverse all commits (Now from old to new).
        var reversedCommits = Util.copyList(commits, Collections::reverse);

        //Working variables to keep track of the current version and the offset.
        String currentVersion = "";
        int offset = 0;

        //Map to store the results.
        Map<String, String> versionMap = new HashMap<>();
        for (RevCommit commit : reversedCommits) {
            //Grab the commit hash.
            var commitHash = commit.toObjectId().name();
            var version = commitHashToVersions.get(commitHash); //Check if we have a tagged commit for a specific identifiable-version.
            if (version != null) {
                //We have a tagged commit, update the current version and set the offset to 0.
                offset = 0;
                currentVersion = version;
            } else {
                //We don't have a tagged commit, increment the offset.
                offset++;
            }

            //Determine the commits version.
            var releasedVersion = currentVersion + "." + offset;
            if (currentVersion.isEmpty()) {
                //We do not have a tagged commit yet.
                //So append the pre-release offset to the version
                releasedVersion = prereleaseTargetVersion + "-pre-$offset";
            }
            versionMap.put(commitHash, releasedVersion);
        }

        return versionMap;
    }

    /**
     * Finds the oldest version in the list of commits.
     *
     * @param commits              The commits to check. (youngest to oldest)
     * @param commitHashToVersions The commit hash to version map.
     * @return The oldest identifiable-version in the list of commits.
     */
    public static String getFirstReleasedVersion(final List<RevCommit> commits, final Map<String, String> commitHashToVersions) {
        String currentVersion = "1.0";
        //Simple loop over all commits (natural order is youngest to oldest)
        for (RevCommit commit : commits) {
            var commitHash = commit.toObjectId().name();
            var version = commitHashToVersions.get(commitHash);
            if (version != null) {
                currentVersion = version;
            }
        }

        //Return the last one found.
        return currentVersion;
    }

    /**
     * Builds a map that matches a commit hash to an identifiable-version (the primary version).
     *
     * @param commits              The commits to check from youngest to oldest.
     * @param commitHashToVersions A commit hash to identifiable-version name map.
     * @return The commit hash to identifiable-version map.
     */
    public static Map<String, String> getPrimaryVersionMap(final List<RevCommit> commits, final Map<String, String> commitHashToVersions) {
        String lastVersion = null;
        List<String> currentVersionCommitHashes = new ArrayList<>();
        Map<String, String> primaryVersionMap = new HashMap<>();

        //Loop over all commits.
        for (RevCommit commit : commits) {
            var commitHash = commit.toObjectId().name();
            currentVersionCommitHashes.add(commitHash); //Collect all commit hashes in the current identifiable version.
            var version = commitHashToVersions.get(commitHash);
            if (version != null) {
                //We found a version boundary (generally a tagged commit is the first build for a given identifiable-version).
                for (String combinedHash : currentVersionCommitHashes) {
                    primaryVersionMap.put(combinedHash, version);
                    lastVersion = version;
                }

                //Reset the collection list.
                currentVersionCommitHashes.clear();
            }
        }

        //We need to deal with repositories without properly tagged versions
        //They are all 1.0-pre-x for now then.
        if (commitHashToVersions.isEmpty())
            lastVersion = "1.0";

        if (lastVersion != null) {
            //Everything that is left over are pre-releases.
            for (String combinedHash : currentVersionCommitHashes) {
                primaryVersionMap.put(combinedHash, lastVersion + "-pre");
            }
        }

        //Return the collected data.
        return primaryVersionMap;
    }

    /**
     * Determine the length of pre commit message prefix for each identifiable-version. This is generally dependent on
     * the amount of releases in each window, more releases means more characters, and this a longer prefix. The prefix
     * length guarantees that all versions in that window will fit in the log, lining up the commit messages vertically
     * under each other.
     *
     * @param availableVersions        The available versions to check. Order does not matter.
     * @param availablePrimaryVersions The available primary versions to check. Order does not matter.
     * @return A map from primary identifiable-version to prefix length.
     */
    public static Map<String, Integer> determinePrefixLengthPerPrimaryVersion(final Collection<String> availableVersions, final Set<String> availablePrimaryVersions) {
        var result = new HashMap<String, Integer>();

        //Sort the versions reversely alphabetically by length (reverse alphabetical order).
        //Needed so that versions which prefix another version are tested later then the versions they are an infix for.
        var sortedVersions = Util.copyList(availablePrimaryVersions, Collections::sort);
        var workingPrimaryVersions = Util.copyList(sortedVersions, Collections::reverse);

        //Loop over each known version.
        for (String version : availableVersions) {
            //Check all primary versions for a prefix match.
            for (String primaryVersion : workingPrimaryVersions) {
                if (!version.startsWith(primaryVersion)) {
                    continue;
                }

                //Check if we have a longer version, if so store.
                var length = version.trim().length();
                if (!result.containsKey(primaryVersion) || result.get(primaryVersion) < length)
                    result.put(primaryVersion, length);

                //Abort the inner loop and continue with the next.
                break;
            }
        }

        return result;
    }

    /**
     * Processes the commit body of a commit stripping out unwanted information.
     *
     * @param body The body to process.
     * @return The result of the processing.
     */
    public static String processCommitBody(final String body) {
        final String[] bodyLines = body.split("\n"); //Split on newlines.
        final List<String> resultingLines = new ArrayList<>();
        for (String bodyLine : bodyLines) {
            if (bodyLine.startsWith("Signed-off-by: ")) //Remove all the signed of messages.
                continue;

            if (bodyLine.trim().isEmpty()) //Remove empty lines.
                continue;

            resultingLines.add(bodyLine);
        }

        return String.join("\n", resultingLines).trim(); //Join the result again.
    }

    public static DisabledSystemConfigReader disableSystemConfig() {
        return disableSystemConfig(SystemReader.getInstance());
    }

    public static DisabledSystemConfigReader disableSystemConfig(SystemReader reader) {
        return new DisabledSystemConfigReader(reader);
    }

    public static final class DisabledSystemConfigReader extends SystemReader.Delegate {
        public final SystemReader parent;

        private DisabledSystemConfigReader(SystemReader parent) {
            super(parent);
            this.parent = parent;
        }

        @Override
        public FileBasedConfig openSystemConfig(Config parent, FS fs) {
            return new FileBasedConfig(parent, null, fs) {
                @Override
                public void load() {}

                @Override
                public boolean isOutdated() {
                    return false;
                }
            };
        }
    }
}
