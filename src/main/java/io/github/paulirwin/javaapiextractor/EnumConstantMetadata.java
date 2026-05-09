package io.github.paulirwin.javaapiextractor;

import java.util.List;

/**
 * An enum constant. List position in {@link TypeMetadata#enumConstants()} is the
 * constant's {@code ordinal()} — order is part of the binary/serialization contract,
 * so this record is intentionally not {@link Comparable}.
 */
public record EnumConstantMetadata(String name,
                                   List<AnnotationMetadata> annotations,
                                   JavadocMetadata javadoc) {
    public EnumConstantMetadata {
        annotations = List.copyOf(annotations);
    }
}
