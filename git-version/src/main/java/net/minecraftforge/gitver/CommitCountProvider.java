package net.minecraftforge.gitver;

import org.eclipse.jgit.api.Git;

import java.util.function.BiFunction;
import java.util.function.Supplier;

@FunctionalInterface
public interface CommitCountProvider {
    int get(Git git, String tag);

    default String getAsString(Git git, String tag, Supplier<String> fallback) {
        try {
            int result = this.get(git, tag);
            if (result > 0) return Integer.toString(result);
        } catch (Exception ignored) { }

        return fallback.get();
    }
}
