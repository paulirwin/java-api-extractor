package io.github.paulirwin.javaapiextractor;

import java.util.List;

public record FieldMetadata(String name,
                            String type,
                            String genericType,
                            List<String> modifiers,
                            List<AnnotationMetadata> annotations,
                            boolean isStatic) implements Comparable<FieldMetadata> {
    public FieldMetadata {
        modifiers = List.copyOf(modifiers);
        annotations = List.copyOf(annotations);
    }

    @Override
    public int compareTo(FieldMetadata other) {
        return this.name.compareTo(other.name);
    }
}
