package io.github.paulirwin.javaapiextractor;

import java.util.List;

public record TypeMetadata(
        String packageName,
        String kind,
        String name,
        String fullName,
        String enclosingType,
        String baseType,
        String genericBaseType,
        List<String> interfaces,
        List<String> genericInterfaces,
        List<String> modifiers,
        List<String> typeParameters,
        List<AnnotationMetadata> annotations,
        List<ConstructorMetadata> constructors,
        List<MethodMetadata> methods,
        List<FieldMetadata> fields) implements Comparable<TypeMetadata> {
    public TypeMetadata {
        interfaces = List.copyOf(interfaces);
        genericInterfaces = List.copyOf(genericInterfaces);
        modifiers = List.copyOf(modifiers);
        typeParameters = List.copyOf(typeParameters);
        annotations = List.copyOf(annotations);
        constructors = List.copyOf(constructors);
        methods = List.copyOf(methods);
        fields = List.copyOf(fields);
    }

    @Override
    public int compareTo(TypeMetadata other) {
        var packageCompare = this.packageName.compareTo(other.packageName);
        if (packageCompare != 0) {
            return packageCompare;
        }
        var fullNameCompare = this.fullName.compareTo(other.fullName);
        if (fullNameCompare != 0) {
            return fullNameCompare;
        }
        return this.kind.compareTo(other.kind);
    }
}
