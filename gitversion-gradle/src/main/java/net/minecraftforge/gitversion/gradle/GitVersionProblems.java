/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitversion.gradle;

import net.minecraftforge.gradleutils.shared.EnhancedProblems;
import org.gradle.api.problems.Severity;

import javax.inject.Inject;

abstract class GitVersionProblems extends EnhancedProblems {
    @Inject
    public GitVersionProblems() {
        super(GitVersionPlugin.NAME, GitVersionPlugin.DISPLAY_NAME);
    }

    void reportGitVersionFailure(String errorOutput, Throwable failure) {
        report("git-version-failure", "Git Version failed to generate version info", spec -> spec
            .details("""
                Git Version failed to generate version information. Please address this issue before attempting to publish your project.
                Error Output:
                %s""".formatted(errorOutput.indent(2)))
            .withException(failure)
            .severity(Severity.ERROR)
            .solution("If you are not in a Git repository, ignore until you initialize it or do not use the Git Version Gradle plugin.")
            .solution("If your Git repository has no remote, add one.")
            .solution("If your Git repository has no tags, add one.")
            .solution(HELP_MESSAGE));
    }
}
