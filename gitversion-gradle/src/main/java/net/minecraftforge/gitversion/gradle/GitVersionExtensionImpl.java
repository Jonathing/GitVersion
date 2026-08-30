package net.minecraftforge.gitversion.gradle;

import net.minecraftforge.gradleutils.GenerateActionsWorkflow;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;

abstract class GitVersionExtensionImpl implements GitVersionExtensionInternal {
    private final Property<Output> gitversion;

    private boolean hasProject;

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProviderFactory getProviders();

    @Inject
    public GitVersionExtensionImpl(GitVersionPlugin plugin, ExtensionAware target, Directory projectDirectory) {
        this.gitversion = getObjects().property(Output.class)
            .value(GitVersionValueSource.of(getObjects().newInstance(GitVersionProblems.class), plugin, projectDirectory));
        this.gitversion.disallowChanges();
        this.gitversion.finalizeValueOnRead();

        if (target instanceof Project project)
            this.attachTo(project);
    }

    @Override
    public void attachTo(Project project) {
        if (this.hasProject) return;

        this.hasProject = true;
        project.afterEvaluate(this::finish);
    }

    @SuppressWarnings("unchecked")
    private void finish(Project project) {
        project.getPluginManager().withPlugin("net.minecraftforge.gradleutils", appliedPlugin ->
            project.getTasks().withType(GenerateActionsWorkflow.class).configureEach(task -> {
                task.getBranch().convention(this.gitversion.map(gitversion -> gitversion.info().getBranch()));
                task.getLocalPath().convention(getProviders().provider(this::getProjectPath));
                task.getPaths().convention(this.gitversion.map(gitversion -> gitversion.subprojectPaths().stream().map("!%s/**"::formatted).toList()));

                try {
                    var gitVersionPresent = (Property<Boolean>) InvokerHelper.getProperty(task, "gitVersionPresent");
                    gitVersionPresent.set(true);
                } catch (RuntimeException e) {
                    // no-op, not worth causing trouble if it's broken for some reason.
                }
            })
        );

        var problems = project.getObjects().newInstance(GitVersionProblems.class);
        if (problems.test("net.minecraftforge.gitversion.log.version")) {
            if (Project.DEFAULT_VERSION.equals(project.getVersion().toString())) {
                project.getLogger().warn("WARNING: Project does not have a version despite applying Git Version Gradle!");
            } else {
                project.getLogger().lifecycle("Version: {}", project.getVersion());
            }
        }
    }

    @Override
    public GitVersionExtension.Info getInfo() {
        return this.gitversion.get().info();
    }

    @Override
    public @Nullable String getUrl() {
        return this.gitversion.get().url();
    }

    @Override
    public String getTagPrefix() {
        return this.gitversion.get().tagPrefix();
    }

    @Override
    public @Unmodifiable Collection<String> getFilters() {
        return this.gitversion.get().filters();
    }

    @Override
    public Directory getGitDir() {
        return getObjects().directoryProperty().fileValue(new File(this.gitversion.get().gitDirPath())).get();
    }

    @Override
    public Directory getRoot() {
        return getObjects().directoryProperty().fileValue(new File(this.gitversion.get().rootPath())).get();
    }

    @Override
    public Directory getProject() {
        return getObjects().directoryProperty().fileValue(new File(this.gitversion.get().projectPath())).get();
    }
}
