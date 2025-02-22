package net.minecraftforge.util.git;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

final class Util {
    @SuppressWarnings("unchecked")
    static <R, E extends Throwable> R sneak(Throwable t) throws E {
        throw (E) t;
    }

    static <T> ArrayList<T> copyList(Collection<T> t, Consumer<? super ArrayList<T>> action) {
        var list = new ArrayList<>(t);
        action.accept(list);
        return list;
    }
}
