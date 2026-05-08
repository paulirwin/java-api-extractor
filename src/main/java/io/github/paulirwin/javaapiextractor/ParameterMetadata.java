package io.github.paulirwin.javaapiextractor;

import java.util.List;

public record ParameterMetadata(String name,
                                String type,
                                String genericType,
                                List<AnnotationMetadata> annotations,
                                JavadocMetadata javadoc)
        implements Comparable<ParameterMetadata> {
    public ParameterMetadata {
        annotations = List.copyOf(annotations);
    }

    @Override
    public int compareTo(ParameterMetadata other) {
        var typeComparison = this.type.compareTo(other.type);
        if (typeComparison != 0) {
            return typeComparison;
        }
        return this.name.compareTo(other.name);
    }
}
