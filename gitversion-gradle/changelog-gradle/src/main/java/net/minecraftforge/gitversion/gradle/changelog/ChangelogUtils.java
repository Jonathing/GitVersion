/*
 * Copyright (c) Forge Development LLC
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gitversion.gradle.changelog;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.jspecify.annotations.Nullable;

final class ChangelogUtils {
    private ChangelogUtils() {}

    /**
     * Adds the createChangelog task to the target project. Also exposes it as a artifact of the 'createChangelog'
     * configuration.
     * <p>
     * This is the
     * <a href="https://docs.gradle.org/current/samples/sample_cross_project_output_sharing.html"> recommended way</a>
     * to share task outputs between multiple projects.
     *
     * @param project Project to add the task to
     * @return The task responsible for generating the changelog
     */
    static TaskProvider<? extends GenerateChangelog> setupChangelogTask(Project project) {
        var tasks = project.getTasks();

        var task = tasks.register(GenerateChangelog.NAME, GenerateChangelogImpl.class);
        project.getConfigurations().register(GenerateChangelog.NAME, c -> c.setCanBeResolved(false));
        project.getArtifacts().add(GenerateChangelog.NAME, task);
        tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME::equals).configureEach(t -> t.dependsOn(task));
        return task;
    }

    /**
     * Sets up the changelog generation on all maven publications in the project.
     * <p>It also sets up publishing for all subprojects as long as that subproject does not have another changelog plugin
     * overriding the propagation.</p>
     *
     * @param project The project to add changelog generation publishing to
     */
    static void setupChangelogGenerationOnAllPublishTasks(Project project, Property<Boolean> includingSubprojects) {
        setupChangelogGenerationForAllPublications(project);

        if (includingSubprojects.getOrElse(false)) {
            project.subprojects(it -> Util.ensureAfterEvaluate(it, subproject -> {
                // attempt to get the current subproject's changelog extension
                var changelog = subproject.getExtensions().findByType(ChangelogExtension.class);

                // find the changelog extension for the highest project that has it, if the subproject doesn't
                for (var parent = project; changelog == null && parent != null; parent = parent.getParent() == parent ? null : parent.getParent()) {
                    changelog = parent.getExtensions().findByType(ChangelogExtension.class);
                }

                // if the project with changelog is publishing all changelogs, set up changelogs for the subproject
                if (changelog != null && changelog.getPublishAll().getOrElse(false))
                    setupChangelogGenerationForAllPublications(subproject);
            }));
        }
    }

    private static void setupChangelogGenerationForAllPublications(Project project) {
        // Get each extension and add the publishing task as a publishing artifact
        var publishing = project.getExtensions().findByType(PublishingExtension.class);
        if (publishing == null) return;

        publishing.getPublications().withType(MavenPublication.class).configureEach(
            publication -> setupChangelogGenerationForPublishing(project, publication)
        );
    }

    private static @Nullable ChangelogExtensionInternal findParent(Project project) {
        var changelog = (ChangelogExtensionInternal) project.getExtensions().findByName(ChangelogExtension.NAME);
        if (changelog != null && changelog.isGenerating()) return changelog;

        var parent = project.getParent();
        return parent == null || parent == project ? null : findParent(parent);
    }

    /**
     * The recommended way to share task outputs across projects is to export them as dependencies
     * <p>
     * So for any project that doesn't generate the changelog directly, we must create a
     * {@linkplain CopyChangelog copy task} and new configuration
     */
    private static @Nullable TaskProvider<? extends Task> findChangelogTask(Project project) {
        var tasks = project.getTasks();
        var taskNames = tasks.getNames();

        // See if we've already made the task
        if (taskNames.contains(GenerateChangelog.NAME))
            return tasks.named(GenerateChangelog.NAME);

        if (taskNames.contains(CopyChangelog.NAME))
            return tasks.named(CopyChangelog.NAME);

        // See if there is any parent with a changelog configured
        var task = findParent(project);
        return task == null ? null : task.copyTo(project);
    }

    /**
     * Sets up the changelog generation on the given maven publication.
     *
     * @param project The project in question
     * @param publication The publication in question
     */
    static void setupChangelogGenerationForPublishing(Project project, MavenPublication publication) {
        Util.ensureAfterEvaluate(project, p -> setupChangelogGenerationForPublishingAfterEvaluation(p, publication));
    }

    private static void setupChangelogGenerationForPublishingAfterEvaluation(Project project, MavenPublication publication) {
        boolean existing = !publication.getArtifacts().matching(a -> "changelog".equals(a.getClassifier()) && "txt".equals(a.getExtension())).isEmpty();
        if (existing) return;

        // Grab the task
        var task = findChangelogTask(project);
        if (task == null) return;

        // Add a new changelog artifact and publish it
        publication.artifact(task.map(t -> t.getOutputs().getFiles().getSingleFile()), artifact -> {
            artifact.builtBy(task);
            artifact.setClassifier("changelog");
            artifact.setExtension("txt");
        });
    }
}
