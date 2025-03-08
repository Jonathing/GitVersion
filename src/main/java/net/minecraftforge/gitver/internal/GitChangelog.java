/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitver.internal;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A utility class for generating changelogs from Git repositories.
 * <p>
 * Typically used by GitVersion in
 * {@link net.minecraftforge.gitver.api.GitVersion#generateChangelog(String, String, boolean)}.
 */
interface GitChangelog {
    /**
     * Generates a changelog string that can be written to a file from a given git directory and repository url.
     * <p>
     * The changes will be generated from the given commit to the given commit.
     *
     * @param git           The Git repository from which to pull the git commit information.
     * @param repositoryUrl The github url of the repository.
     * @param plainText     Indicates if plain text ({@code true}) should be used, or changelog should be used
     *                      ({@code false}).
     * @param start         The commit hash of the commit to use as the beginning of the changelog.
     * @param end           The commit hash of the commit to use as the end of the changelog.
     * @param tagPrefix     The tag prefix to filter tags with.
     * @param filter        The filter to decide how to ignore certain commits.
     * @return A multiline changelog string.
     */
    static String generateChangelogFromTo(Git git, String repositoryUrl, boolean plainText, RevCommit start, RevCommit end, @Nullable String tagPrefix, Iterable<String> filter) throws GitAPIException, IOException {
        var endCommitHash = end.toObjectId().getName(); //Grab the commit hash of the end commit.
        var startCommitHash = start.toObjectId().getName(); //Grab the commit hash of the start commit.

        var changeLogName = Util.replace(git.getRepository().getFullBranch(), s -> s.replace("refs/heads/", "")); //Generate a changelog name from the current branch.

        var log = GitUtils.getCommitLogFromTo(git, start, end, filter); //Get all commits between the start and the end.
        var logList = new ArrayList<RevCommit>(); //And generate a list from it.
        log.forEach(logList::add);

        var tagMap = GitUtils.getCommitToTagMap(git, tagPrefix); //Grab a map between commits and tag names.
        var versionMap = GitUtils.buildVersionMap(logList, tagMap); //And generate a version map from this. Mapping each commit to a unique version.
        var primaryVersionMap = GitUtils.getPrimaryVersionMap(logList, tagMap); //Then determine which commits belong to which identifiable-version mappings.

        //Determine the length of each identifiable-versions max-length commit specific version.
        //(How wide does the area in-front of the commit message need to be to fit all versions in the current identifiable-version?)
        var primaryVersionPrefixLengthMap = GitUtils.determinePrefixLengthPerPrimaryVersion(versionMap.values(), new HashSet<String>(primaryVersionMap.values()));

        //Generate the header
        var changelog = new StringBuilder();
        changelog.append(plainText
            ? "%s Changelog\n".formatted(changeLogName)
            : "### [%s Changelog](%s/compare/%s...%s)%n".formatted(changeLogName, repositoryUrl, startCommitHash, endCommitHash));

        //Some working variables and processing patterns.
        var currentPrimaryVersion = ""; //The current identifiable-version.
        var pullRequestPattern = Pattern.compile("\\(#(?<pullNumber>[0-9]+)\\)"); //A Regex pattern to find PullRequest numbers in commit messages.

        //Loop over all commits and append their message as a changelog.
        //(They are already in order from newest to oldest, so that works out for us.)
        for (RevCommit commit : logList) {
            var commitHash = commit.toObjectId().name(); //Get the commit hash, so we can look it up in maps.

            var requiresVersionHeader = false; //Indicates later on if we need to inject a new version header.
            if (primaryVersionMap.containsKey(commitHash)) {
                var versionsPrimaryVersion = primaryVersionMap.get(commitHash); //The current commits primary version.
                requiresVersionHeader = !Objects.equals(versionsPrimaryVersion, currentPrimaryVersion); //Check if we need a new one.
                currentPrimaryVersion = versionsPrimaryVersion; //Update the cached version.
            }

            //Generate a version header if required.
            if (requiresVersionHeader && plainText) {
                changelog.append(currentPrimaryVersion).append('\n');
                //noinspection SuspiciousRegexArgument
                changelog.append(currentPrimaryVersion.replaceAll(".", "=")).append('\n');
            }

            //Generate the commit message prefix.
            var commitHeader = new StringBuilder();
            commitHeader.append(" - ");
            if (versionMap.containsKey(commitHash)) {
                var version = versionMap.get(commitHash);
                var commitHeaderVersion = "%s".formatted(padRight(version, primaryVersionPrefixLengthMap.getOrDefault(currentPrimaryVersion, 0)));
                var commitHeaderUrl = "(%s/tree/%s)".formatted(repositoryUrl, version);
                commitHeader.append(tagMap.containsKey(commitHash) && !plainText ? "[%s]%s".formatted(commitHeaderVersion, commitHeaderUrl) : commitHeaderVersion);
            }

            var commitHeaderLength = commitHeader.length();
            commitHeader.append(' ');
            var noneCommitHeaderPrefix = String.join("", Collections.nCopies(commitHeaderLength, " ")) + " "; //Generate a prefix for each line in the commit message so that it lines up.

            //Get a processed commit message body.
            var subject = GitUtils.processCommitBody(commit.getFullMessage().trim());

            //If we generate changelog, then process the pull request numbers.
            if (!plainText) {
                //Check if we have a pull request.
                var matcher = pullRequestPattern.matcher(subject);
                if (matcher.find()) {
                    //Grab the number
                    var pullRequestNumber = matcher.group("pullNumber");

                    //Replace the pull request number.
                    subject = subject.replace("#%s".formatted(pullRequestNumber), "[#%s](%s/pull/%s)".formatted(pullRequestNumber, repositoryUrl, pullRequestNumber));
                }
            }

            //Replace each newline in the message with a newline and a prefix so the message lines up.
            subject = subject.replaceAll("\\n", "\n" + noneCommitHeaderPrefix);

            //Append the generated entry with its header (list entry + version number)
            changelog.append(commitHeader).append(subject);
            changelog.append('\n');

            //When we are done writing the last entry, add a newline.
            if (tagMap.containsKey(commitHash) && plainText)
                changelog.append('\n');
        }

        return changelog.toString();
    }

    private static String padRight(CharSequence self, Number numberOfChars) {
        return padRight(self, numberOfChars, " ");
    }

    private static String padRight(CharSequence self, Number numberOfChars, CharSequence padding) {
        int numChars = numberOfChars.intValue();
        return numChars <= self.length() ? self.toString() : self + getPadding(padding.toString(), numChars - self.length());
    }

    private static String getPadding(CharSequence padding, int length) {
        return padding.length() < length ? multiply(padding, length / padding.length() + 1).substring(0, length) : "" + padding.subSequence(0, length);
    }

    private static String multiply(CharSequence self, Number factor) {
        int size = factor.intValue();
        if (size == 0) {
            return "";
        } else if (size < 0) {
            throw new IllegalArgumentException("multiply() should be called with a number of 0 or greater not: " + size);
        } else {
            return self + String.valueOf(self).repeat(size - 1);
        }
    }
}
