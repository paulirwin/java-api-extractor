package io.github.paulirwin.javaapiextractor;

import java.util.List;

public record MethodMetadata(String name,
                             String returnType,
                             String genericReturnType,
                             List<ParameterMetadata> parameters,
                             List<String> modifiers,
                             List<String> typeParameters,
                             List<String> throwsTypes,
                             List<AnnotationMetadata> annotations,
                             boolean isVarArgs) implements Comparable<MethodMetadata> {
    public MethodMetadata {
        parameters = List.copyOf(parameters);
        modifiers = List.copyOf(modifiers);
        typeParameters = List.copyOf(typeParameters);
        throwsTypes = List.copyOf(throwsTypes);
        annotations = List.copyOf(annotations);
    }

    @Override
    public int compareTo(MethodMetadata other) {
        var nameComparison = this.name.compareTo(other.name);
        if (nameComparison != 0) {
            return nameComparison;
        }

        if (this.parameters.size() != other.parameters.size()) {
            return this.parameters.size() - other.parameters.size();
        }

        for (int i = 0; i < this.parameters.size(); i++) {
            var paramComparison = this.parameters.get(i).compareTo(other.parameters.get(i));
            if (paramComparison != 0) {
                return paramComparison;
            }
        }

        return this.returnType.compareTo(other.returnType);
    }
}
