package io.github.paulirwin.javaapiextractor;

import java.util.List;

public record ConstructorMetadata(
        List<ParameterMetadata> parameters,
        List<String> modifiers,
        List<String> throwsTypes,
        List<AnnotationMetadata> annotations,
        boolean isVarArgs) implements Comparable<ConstructorMetadata> {
    public ConstructorMetadata {
        parameters = List.copyOf(parameters);
        modifiers = List.copyOf(modifiers);
        throwsTypes = List.copyOf(throwsTypes);
        annotations = List.copyOf(annotations);
    }

    @Override
    public int compareTo(ConstructorMetadata other) {
        if (this.parameters.size() != other.parameters.size()) {
            return this.parameters.size() - other.parameters.size();
        }
        for (int i = 0; i < this.parameters.size(); i++) {
            var paramComparison = this.parameters.get(i).compareTo(other.parameters.get(i));
            if (paramComparison != 0) {
                return paramComparison;
            }
        }
        return 0;
    }
}
