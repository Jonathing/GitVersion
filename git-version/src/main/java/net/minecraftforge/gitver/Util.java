package net.minecraftforge.gitver;

import net.minecraftforge.unsafe.UnsafeHacks;
import org.eclipse.jgit.api.DescribeCommand;
import org.eclipse.jgit.errors.InvalidPatternException;
import org.eclipse.jgit.fnmatch.FileNameMatcher;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class Util {
    @SuppressWarnings("unchecked")
    static <R, E extends Throwable> R sneak(Throwable t) throws E {
        throw (E) t;
    }

    static @Nullable List<String> rsplit(@Nullable String input, String del) {
        return rsplit(input, del, -1);
    }

    static @Nullable List<String> rsplit(@Nullable String input, String del, int limit) {
        if (input == null) return null;
        List<String> lst = new ArrayList<>();
        int x = 0;
        int idx;
        String tmp = input;
        while ((idx = tmp.lastIndexOf(del)) != -1 && (limit == -1 || x++ < limit)) {
            lst.add(0, tmp.substring(idx + del.length(), tmp.length()));
            tmp = tmp.substring(0, idx);
        }
        lst.add(0, tmp);
        return lst;
    }

    static <T> T make(Supplier<T> t) {
        return t.get();
    }

    static <T> T make(T t, Consumer<T> action) {
        action.accept(t);
        return t;
    }

    static <T, R> R modify(T t, Function<T, R> action) {
        return action.apply(t);
    }

    static <C extends Collection<T>, T> C make(C c, T[] array) {
        if (array != null) c.addAll(Arrays.asList(array));
        return c;
    }

    static <T> Collection<T> ensure(Collection<T> c) {
        return c == null ? Collections.emptyList() : c;
    }

    static int count(Iterable<?> i) {
        if (i instanceof Collection<?> c) return c.size();

        int count = -1;
        for (Object o : i) count++;
        return count;
    }

    // TODO [GitVersion] Remove once upgraded to JGit 7.2
    @Deprecated(forRemoval = true)
    static void setExclude(DescribeCommand it, String pattern) throws InvalidPatternException {
        class FileNameExcluder extends FileNameMatcher {
            public FileNameExcluder(String patternString) throws InvalidPatternException {
                super(patternString, null);
            }

            @Override
            public boolean isMatch() {
                return !super.isMatch();
            }
        }

        UnsafeHacks.<DescribeCommand, List<FileNameMatcher>>findField(DescribeCommand.class, "matchers").get(it).add(new FileNameExcluder(pattern));
    }
}
