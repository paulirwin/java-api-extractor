package io.github.paulirwin.javaapiextractor;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JavadocMetadata(
        String description,
        Map<String, String> tags,
        String rawText) implements Comparable<JavadocMetadata> {
    public JavadocMetadata {
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }

    @Override
    public int compareTo(JavadocMetadata other) {
        var descCompare = (description == null ? "" : description)
                .compareTo(other.description == null ? "" : other.description);
        if (descCompare != 0) {
            return descCompare;
        }
        var tagsCompare = tags.toString().compareTo(other.tags.toString());
        if (tagsCompare != 0) {
            return tagsCompare;
        }
        return (rawText == null ? "" : rawText)
                .compareTo(other.rawText == null ? "" : other.rawText);
    }
}
