/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitver.internal;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for common git operations.
 * <p>
 * This is used heavily by, and in conjunction with, GitVersion. It holds the majority of operations done on the Git
 * repository. These utilities as a part of {@code git-utils} instead of {@code git-version} so that they can be used by
 * other projects without needing to interface or bundle with GitVersion.
 */
public interface GitUtils {
    List<String> DEFAULT_ALLOWED_BRANCHES = List.of("master", "main", "HEAD");

    /**
     * Gets the relative path of a file from a root directory. Uses NIO's {@link Path} to guarantee cross-platform
     * compatibility and reproducible path strings.
     *
     * @param root The root path
     * @param path The path to get the relative of from the root
     * @return The relative path
     */
    static String getRelativePath(Path root, Path path) {
        return root.relativize(path).toString().replace(root.getFileSystem().getSeparator(), "/");
    }

    /** @see #getRelativePath(Path, Path) */
    static String getRelativePath(File root, File file) {
        if (root.equals(file)) return "";
        return getRelativePath(root.toPath(), file.toPath());
    }

    /**
     * Attempts to find the git root from the given directory.
     *
     * @param from The file to find the Git root from
     * @return The Git root, or the given file if no Git root was found
     */
    static File findGitRoot(File from) {
        for (var dir = from.getAbsoluteFile(); dir != null; dir = dir.getParentFile())
            if (isGitRoot(dir)) return dir;

        return from;
    }

    /**
     * Checks if a given file is a Git root.
     *
     * @param dir The directory to check
     * @return {@code true} if the directory is a Git root
     */
    static boolean isGitRoot(File dir) {
        return new File(dir, ".git").exists();
    }

    /**
     * Counts commits, for the given Git repository, from the given tag to {@linkplain Constants#HEAD HEAD}. If the
     * given tag cannot be found, this method will return {@code -1}.
     * <p>
     * See {@link #countCommits(Git, ObjectId, Iterable, Iterable)} for more information.
     *
     * @param git          The git repository to count commits in
     * @param tag          The tag name to start counting from
     * @param includePaths The paths to include in the count
     * @param excludePaths The paths to exclude from the count
     * @return The commit count
     * @throws GitAPIException If an error occurs when running the log command (see
     *                         {@link org.eclipse.jgit.api.LogCommand#call() LogCommand.call()}
     * @throws IOException     If an I/O error occurs when reading the Git repository
     * @see #countCommits(Git, ObjectId, Iterable, Iterable)
     */
    static int countCommits(Git git, String tag, Iterable<String> includePaths, Iterable<String> excludePaths) throws GitAPIException, IOException {
        var tags = GitUtils.getTagToCommitMap(git);
        var commitHash = tags.get(tag);
        if (commitHash == null) return -1;

        return countCommits(git, ObjectId.fromString(commitHash), includePaths, excludePaths);
    }

    /**
     * Counts commits, for the given Git repository, from the given object ID to {@linkplain Constants#HEAD HEAD}.
     * Additional paths can be given to include or exclude from the count.
     * <p>
     * An important detail to note is that the commit given is <strong>not included</strong> in the count. This means
     * that if the given commit is also the HEAD, the returned count will be {@code 0}. Additionally, if there are no
     * commits barring the paths given from the object ID, the count will be {@code -1}. Please handle this
     * accordingly.
     *
     * @param git          The git repository to count commits in
     * @param from         The object ID (typically a commit or tag reference) to start counting from
     * @param includePaths The paths to include in the count
     * @param excludePaths The paths to exclude from the count
     * @return The commit count
     * @throws GitAPIException If an error occurs when running the log command (see
     *                         {@link org.eclipse.jgit.api.LogCommand#call() LogCommand.call()}
     * @throws IOException     If an I/O error occurs when reading the Git repository
     * @see org.eclipse.jgit.api.LogCommand LogCommand
     * @see <a href="https://git-scm.com/docs/git-log"><code>git-log</code></a>
     */
    static int countCommits(Git git, ObjectId from, Iterable<String> includePaths, Iterable<String> excludePaths) throws GitAPIException, IOException {
        return Util.count(getCommitLogFromTo(git, from, getHead(git), includePaths, excludePaths));
    }

    /**
     * Gets the {@linkplain Constants#HEAD HEAD} commit of the given Git repository.
     *
     * @param git The Git repository to get the HEAD commit from
     * @return The HEAD commit
     * @throws IOException If an I/O error occurs when reading the Git repository
     */
    static RevCommit getHead(Git git) throws IOException {
        return getCommitFromId(git, git.getRepository().resolve(Constants.HEAD));
    }

    /**
     * Determines the commit that the given {@link Ref} references.
     *
     * @param git   The Git repository to get the commit from
     * @param other The reference to get the commit for
     * @return The referenced commit
     * @throws IOException If an I/O error occurs when reading the Git repository
     */
    static RevCommit getCommitFromRef(Git git, Ref other) throws IOException {
        return getCommitFromId(git, other.getObjectId());
    }

    /**
     * Determines the commit that the given {@link ObjectId} references.
     *
     * @param git   The Git repository to get the commit from
     * @param other The object ID to get the commit for
     * @return The referenced commit
     * @throws IOException If an I/O error occurs when reading the Git repository
     */
    static RevCommit getCommitFromId(Git git, ObjectId other) throws IOException {
        if (other instanceof RevCommit commit) return commit;

        try (RevWalk revWalk = new RevWalk(git.getRepository())) {
            return revWalk.parseCommit(other);
        }
    }

    /**
     * Gets the commit log from the start commit to the end.
     *
     * @param git          The Git repository to get the commits from
     * @param from         The commit to start from (the oldest)
     * @param to           The end commit (the youngest)
     * @param includePaths The paths to include in the count
     * @return The commit log (youngest to oldest)
     * @throws GitAPIException If an error occurs when running the log command (see
     *                         {@link org.eclipse.jgit.api.LogCommand#call() LogCommand.call()}
     * @throws IOException     If an I/O error occurs when reading the Git repository
     */
    static Iterable<RevCommit> getCommitLogFromTo(Git git, ObjectId from, ObjectId to, Iterable<String> includePaths) throws GitAPIException, IOException {
        return getCommitLogFromTo(git, from, to, includePaths, Collections::emptyIterator);
    }

    /**
     * Gets the commit log from the start commit to the end.
     *
     * @param git          The Git repository to get the commits from
     * @param from         The commit to start from (the oldest)
     * @param to           The end commit (the youngest)
     * @param includePaths The paths to include in the log
     * @param excludePaths The paths to exclude from the log
     * @return The commit log (youngest to oldest)
     * @throws GitAPIException If an error occurs when running the log command (see
     *                         {@link org.eclipse.jgit.api.LogCommand#call() LogCommand.call()}
     * @throws IOException     If an I/O error occurs when reading the Git repository
     */
    static Iterable<RevCommit> getCommitLogFromTo(Git git, ObjectId from, ObjectId to, Iterable<String> includePaths, Iterable<String> excludePaths) throws GitAPIException, IOException {
        var start = getCommitFromId(git, from);
        var end = getCommitFromId(git, to);

        var log = git.log().add(end);

        // If our starting commit contains at least one parent (it is not the 'root' commit), exclude all of those parents
        for (var parent : start.getParents())
            log.not(parent);
        // We do not exclude the starting commit itself, so the commit is present in the returned iterable

        for (var path : includePaths)
            log.addPath(path);

        for (var path : excludePaths)
            log.excludePath(path);

        return log.call();
    }

    /**
     * Builds a map of commit hashes to tag names.
     *
     * @param git The Git repository to get the tags from.
     * @return The commit hashes to tag map.
     */
    static Map<String, String> getCommitToTagMap(Git git) throws GitAPIException, IOException {
        return getCommitToTagMap(git, null);
    }

    /**
     * Builds a map of commit hashes to tag names.
     *
     * @param git The Git repository to get the tags from
     * @return The commit hashes to tag map
     */
    static Map<String, String> getCommitToTagMap(Git git, @Nullable String tagPrefix) throws GitAPIException, IOException {
        var versionMap = new HashMap<String, String>();
        for (Ref tag : git.tagList().call()) {
            var tagId = git.getRepository().getRefDatabase().peel(tag).getPeeledObjectId();
            if (tagId == null) tagId = tag.getObjectId();
            if (!StringUtils.isEmptyOrNull(tagPrefix) && !tagId.name().startsWith(tagPrefix)) continue;

            versionMap.put(tagId.name(), tag.getName().replace(Constants.R_TAGS, ""));
        }

        return versionMap;
    }

    /**
     * Builds a map of tag name to commit hash.
     *
     * @param git The Git repository to get the tags from
     * @return The tags to commit hash map
     */
    static Map<String, String> getTagToCommitMap(Git git) throws GitAPIException, IOException {
        return getTagToCommitMap(git, null);
    }

    /**
     * Builds a map of tag name to commit hash.
     *
     * @param git The Git repository to get the tags from
     * @return The tags to commit hash map
     */
    static Map<String, String> getTagToCommitMap(Git git, @Nullable String tagPrefix) throws GitAPIException, IOException {
        Map<String, String> versionMap = new HashMap<>();
        for (Ref tag : git.tagList().call()) {
            ObjectId tagId = git.getRepository().getRefDatabase().peel(tag).getPeeledObjectId();
            if (tagId == null) tagId = tag.getObjectId();
            if (!StringUtils.isEmptyOrNull(tagPrefix) && !tagId.name().startsWith(tagPrefix)) continue;

            versionMap.put(tag.getName().replace(Constants.R_TAGS, ""), tagId.name());
        }

        return versionMap;
    }

    static RevCommit getFirstCommitInRepository(Git git) throws GitAPIException {
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
     * @param git The Git repository to find the merge base in
     * @return The merge base commit or null
     */
    static @Nullable RevCommit getMergeBaseCommit(Git git) throws GitAPIException, IOException {
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
     * @param git The Git repository to get the branches from
     * @return A list of remote branches
     */
    static List<Ref> getAvailableRemoteBranches(Git git) throws GitAPIException {
        return git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
    }

    /**
     * Get the merge base commit between the current and the given branch.
     *
     * @param git   The Git repository to get the merge base in
     * @param other The other branch to find the merge base with
     * @return A merge base commit or null
     */
    static RevCommit getMergeBase(Git git, Ref other) {
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
     * @param commits              The commits to build the version map for
     * @param commitHashToVersions A commit hash to identifiable-version name map
     * @return The commit hash to calculated version map
     */
    static Map<String, String> buildVersionMap(List<RevCommit> commits, Map<String, String> commitHashToVersions) {
        //Determine the version that sets the first fixed version commit.
        var prereleaseTargetVersion = getFirstReleasedVersion(commits, commitHashToVersions);
        //Inverse all commits (Now from old to new).
        var reversedCommits = Util.copyList(commits, Collections::reverse);

        //Working variables to keep track of the current version and the offset.
        var currentVersion = "";
        int offset = 0;

        //Map to store the results.
        var versionMap = new HashMap<String, String>();
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
                releasedVersion = prereleaseTargetVersion + "-pre-%s".formatted(offset);
            }
            versionMap.put(commitHash, releasedVersion);
        }

        return versionMap;
    }

    /**
     * Finds the oldest version in the list of commits.
     *
     * @param commits              The commits to check. (youngest to oldest)
     * @param commitHashToVersions The commit hash to version map
     * @return The oldest identifiable-version in the list of commits
     */
    static String getFirstReleasedVersion(List<RevCommit> commits, Map<String, String> commitHashToVersions) {
        String currentVersion = "0.0";
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
     * @param commits              The commits to check from youngest to oldest
     * @param commitHashToVersions A commit hash to identifiable-version name map
     * @return The commit hash to identifiable-version map
     */
    static Map<String, String> getPrimaryVersionMap(List<RevCommit> commits, Map<String, String> commitHashToVersions) {
        String lastVersion = null;
        var currentVersionCommitHashes = new ArrayList<String>();
        var primaryVersionMap = new HashMap<String, String>();

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
     * @param availableVersions        The available versions to check. Order does not matter
     * @param availablePrimaryVersions The available primary versions to check. Order does not matter
     * @return A map from primary identifiable-version to prefix length
     */
    static Map<String, Integer> determinePrefixLengthPerPrimaryVersion(Collection<String> availableVersions, Set<String> availablePrimaryVersions) {
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
     * @param body The body to process
     * @return The result of the processing
     */
    static String processCommitBody(String body) {
        var bodyLines = body.split("\n"); //Split on newlines.
        var resultingLines = new ArrayList<String>();
        for (String bodyLine : bodyLines) {
            if (bodyLine.startsWith("Signed-off-by: ")) //Remove all the signed of messages.
                continue;

            if (bodyLine.trim().isEmpty()) //Remove empty lines.
                continue;

            resultingLines.add(bodyLine);
        }

        return String.join("\n", resultingLines).trim(); //Join the result again.
    }

    /**
     * Builds a path url for a path under the minecraft forge organisation.
     *
     * @param project The name of the path
     * @return The path GitHub url
     */
    static String buildProjectUrl(String project) {
        return buildProjectUrl("MinecraftForge", project);
    }

    /**
     * Builds a path url for a path under the given organisation.
     *
     * @param organization The name of the org
     * @param project      The name of the path
     * @return The path GitHub url
     */
    static String buildProjectUrl(String organization, String project) {
        return buildProjectUrl("github.com", organization, project);
    }

    /**
     * Builds a path url for a path under the given domain and organisation.
     *
     * @param domain       The domain of the path
     * @param organization The name of the org
     * @param project      The name of the path
     * @return The path URL
     */
    static String buildProjectUrl(String domain, String organization, String project) {
        return "https://%s/%s/%s".formatted(domain, organization, project);
    }

    /**
     * Builds the repository url from the origin remote's push uri. The URI is processed from three different variants
     * into the URL:
     * <ol>
     *     <li>If the protocol is http(s) based then {@literal ".git"} is stripped and returned as url.</li>
     *     <li>If the protocol is ssh and does contain authentication information then the username and password are
     *     stripped and the url is returned without the {@literal ".git"} ending.</li>
     *     <li>If the protocol is ssh and does not contain authentication information then the protocol is switched to
     *     https and the {@literal ".git"} ending is stripped.</li>
     * </ol>
     *
     * @param git The Git repository
     * @return The path URL
     */
    static @Nullable String buildProjectUrl(Git git) {
        List<RemoteConfig> remotes;
        try {
            remotes = git.remoteList().call();
            if (remotes.isEmpty()) return null;
        } catch (GitAPIException e) {
            return null;
        }

        //Get the origin remote.
        var originRemote = Util.findFirst(remotes, remote -> "origin".equals(remote.getName()));

        //We do not have an origin named remote
        if (originRemote == null) return null;

        //Get the origin push url.
        var originUrl = Util.findFirst(originRemote.getURIs());

        //We do not have a origin url
        if (originUrl == null) return null;

        //Grab its string representation and process.
        var originUrlString = originUrl.toString();
        //Determine the protocol
        if (originUrlString.startsWith("ssh")) {
            //If ssh then check for authentication data.
            if (originUrlString.contains("@")) {
                //We have authentication data: Strip it.
                return "https://" + originUrlString.substring(originUrlString.indexOf("@") + 1).replace(".git", "");
            } else {
                //No authentication data: Switch to https.
                return "https://" + originUrlString.substring(6).replace(".git", "");
            }
        } else if (originUrlString.startsWith("http")) {
            //Standard http protocol: Strip the ".git" ending only.
            return originUrlString.replace(".git", "");
        }

        //What other case exists? Just to be sure lets return this.
        return originUrlString;
    }
}
