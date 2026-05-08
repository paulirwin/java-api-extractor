package io.github.paulirwin.javaapiextractor;

public record AnnotationMetadata(String type) implements Comparable<AnnotationMetadata> {
    @Override
    public int compareTo(AnnotationMetadata other) {
        return this.type.compareTo(other.type);
    }
}
