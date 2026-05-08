package io.github.paulirwin.javaapiextractor;

import java.util.ArrayList;
import java.util.List;

public record AnnotationMetadata(String type, List<AnnotationArgument> arguments)
        implements Comparable<AnnotationMetadata> {

    public AnnotationMetadata {
        // Sort arguments by name for stable serialization and comparison: javac may
        // surface element values in source-declaration order, but the JSON output and
        // compareTo result must not depend on that.
        var copy = new ArrayList<>(arguments);
        copy.sort(AnnotationArgument::compareTo);
        arguments = List.copyOf(copy);
    }

    @Override
    public int compareTo(AnnotationMetadata other) {
        int c = this.type.compareTo(other.type);
        if (c != 0) {
            return c;
        }
        // Same annotation type, possibly different element values.
        var a = this.arguments;
        var b = other.arguments;
        int min = Math.min(a.size(), b.size());
        for (int i = 0; i < min; i++) {
            int byName = a.get(i).name().compareTo(b.get(i).name());
            if (byName != 0) {
                return byName;
            }
            int byValue = a.get(i).value().compareTo(b.get(i).value());
            if (byValue != 0) {
                return byValue;
            }
        }
        return Integer.compare(a.size(), b.size());
    }
}
