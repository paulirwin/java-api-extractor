package io.github.paulirwin.javaapiextractor;

/**
 * One element-value pair from an annotation use site, e.g. {@code since = "9"} in
 * {@code @Deprecated(since = "9")}.
 */
public record AnnotationArgument(String name, AnnotationValue value)
        implements Comparable<AnnotationArgument> {
    @Override
    public int compareTo(AnnotationArgument other) {
        return this.name.compareTo(other.name);
    }
}
