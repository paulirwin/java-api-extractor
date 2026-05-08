package io.github.paulirwin.javaapiextractor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonSerializerTest {

    @Test
    void serializesMavenCoordinates() {
        var json = JsonSerializer.serialize(new MavenCoordinates("org.foo", "bar", "1.0"));
        assertTrue(json.contains("\"groupId\":\"org.foo\""));
        assertTrue(json.contains("\"artifactId\":\"bar\""));
        assertTrue(json.contains("\"version\":\"1.0\""));
    }

    @Test
    void serializesLibraryResultWithTypesArray() {
        var result = new LibraryResult(
                new MavenCoordinates("g", "a", "v"),
                List.of());
        var json = JsonSerializer.serialize(result);

        assertTrue(json.contains("\"library\""));
        assertTrue(json.contains("\"types\":[]"));
    }

    @Test
    void serializesTypeMetadataFieldsAsCamelCase() {
        var type = new TypeMetadata(
                "pkg", "class", "Foo", "pkg.Foo", null, null, null,
                List.of(), List.of(), List.of("public"), List.of(), List.of(),
                List.of(), List.of(), List.of());
        var json = JsonSerializer.serialize(type);

        assertTrue(json.contains("\"packageName\":\"pkg\""));
        assertTrue(json.contains("\"fullName\":\"pkg.Foo\""));
        assertTrue(json.contains("\"enclosingType\":null"));
        assertTrue(json.contains("\"constructors\":[]"));
    }

    @Test
    void serializesAnnotationMetadataWithEmptyArguments() {
        var ann = new AnnotationMetadata("java.lang.Deprecated", List.of());
        var json = JsonSerializer.serialize(ann);
        assertEquals("{\"type\":\"java.lang.Deprecated\",\"arguments\":[]}", json);
    }

    @Test
    void serializesAnnotationMetadataWithArguments() {
        var ann = new AnnotationMetadata("java.lang.Deprecated", List.of(
                new AnnotationArgument("forRemoval", new AnnotationValue.BooleanValue(true)),
                new AnnotationArgument("since", new AnnotationValue.StringValue("9"))));
        var json = JsonSerializer.serialize(ann);
        // Arguments are sorted alphabetically by name on construction.
        assertEquals(
                "{\"type\":\"java.lang.Deprecated\",\"arguments\":["
                        + "{\"name\":\"forRemoval\",\"value\":{\"kind\":\"boolean\",\"value\":true}},"
                        + "{\"name\":\"since\",\"value\":{\"kind\":\"string\",\"value\":\"9\"}}]}",
                json);
    }

    @Test
    void isStableAcrossCalls() {
        var a = new MavenCoordinates("g", "a", "1");
        assertEquals(JsonSerializer.serialize(a), JsonSerializer.serialize(a));
    }
}
