package net.minecraftforge.gitversion.gradle;

import net.minecraftforge.gitversion.gradle.common.GitVersionTools;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.gradle.process.JavaExecSpec;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

abstract class GitVersionValueSource implements ValueSource<GitVersionValueResult, GitVersionValueSource.Parameters> {
    interface Parameters extends ValueSourceParameters {
        ConfigurableFileCollection getClasspath();

        DirectoryProperty getProjectPath();
    }

    protected abstract @Inject ExecOperations getExecOperations();

    @Inject
    public GitVersionValueSource() {
    }

    static GitVersionExtensionInternal.Output of(GitVersionProblems problems, GitVersionPlugin plugin, Directory projectDirectory) {
        var result = plugin.getProviders().of(GitVersionValueSource.class, spec -> {
            spec.parameters(parameters -> {
                parameters.getClasspath().from(plugin.getTool(GitVersionTools.GITVERSION).getClasspath());
                // NOTE: We are NOT manually setting the java launcher this time
                //  Gradle 9 requires Java 17 to run, and so does Git Version
                //  We can count on the daemon JVM being 17 or higher without needing the project to apply the 'java' plugin

                parameters.getProjectPath().set(projectDirectory);
            });
        }).get();

        if (result.execFailure() != null)
            problems.reportGitVersionFailure(result.errorOutput(), result.execFailure());

        return result.output();
    }

    @Override
    public GitVersionValueResult obtain() {
        var parameters = this.getParameters();
        var stdOut = new ByteArrayOutputStream();
        var stdErr = new ByteArrayOutputStream();

        GitVersionExtensionInternal.Output output;
        String errorOutput;
        Throwable execFailure;

        Action<? super JavaExecSpec> javaExecSpec = exec -> {
            exec.setClasspath(parameters.getClasspath());
            exec.getMainClass().set(GitVersionTools.GITVERSION.getMainClass());

            exec.setStandardOutput(stdOut);
            exec.setErrorOutput(stdErr);

            exec.args(
                "--json",
                "--project-dir", parameters.getProjectPath().getLocationOnly().get().getAsFile()
            );
        };

        try {
            getExecOperations().javaexec(javaExecSpec).rethrowFailure().assertNormalExitValue();

            output = Util.fromJson(stdOut.toString(StandardCharsets.UTF_8), GitVersionExtensionInternal.Output.class);
            errorOutput = stdErr.toString(StandardCharsets.UTF_8);
            execFailure = null;
        } catch (Exception e) {
            stdOut.reset();

            try {
                getExecOperations().javaexec(exec -> {
                    javaExecSpec.execute(exec);


                    exec.args("--disable-strict");
                    exec.setErrorOutput(new ByteArrayOutputStream());
                }).rethrowFailure().assertNormalExitValue();

                output = Util.fromJson(stdOut.toString(StandardCharsets.UTF_8), GitVersionExtensionInternal.Output.class);
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
                output = GitVersionExtensionInternal.Output.EMPTY;
            }

            errorOutput = stdErr.toString(StandardCharsets.UTF_8);
            execFailure = e;
        }

        return new GitVersionValueResult(output, errorOutput, execFailure);
    }
}
