package edu.usc.splitsmart.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Structural copying only; no sorting, normalization, or financial validation. */
public final class ImmutableCollections {
    private ImmutableCollections() {}

    public static <T> List<T> list(List<T> source) {
        List<T> copy = new ArrayList<>(Objects.requireNonNull(source, "source"));
        for (T value : copy) Objects.requireNonNull(value, "collection element");
        return Collections.unmodifiableList(copy);
    }

    public static <K, V> Map<K, V> map(Map<K, V> source) {
        Map<K, V> copy = new LinkedHashMap<>(Objects.requireNonNull(source, "source"));
        for (Map.Entry<K, V> entry : copy.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "map key");
            Objects.requireNonNull(entry.getValue(), "map value");
        }
        return Collections.unmodifiableMap(copy);
    }
}
