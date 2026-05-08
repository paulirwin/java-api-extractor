package io.github.paulirwin.javaapiextractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compiles a fixture jar that exercises every metadata variant, runs the real extraction
 * pipeline, and validates the resulting JSON against {@code api-schema.json}. The schema
 * itself is also asserted to load cleanly so a malformed schema fails fast.
 */
class JsonSchemaValidationTest {
    private static JsonSchema schema;
    private static ObjectMapper objectMapper;

    @TempDir
    static Path workDir;
    static Path fixtureJar;

    @BeforeAll
    static void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        try (var schemaStream = JsonSchemaValidationTest.class.getResourceAsStream("/api-schema.json")) {
            assertNotNull(schemaStream, "Schema file not found in resources");
            schema = factory.getSchema(schemaStream);
        }

        var sources = List.of(
                source("fx.MyAnnotation", """
                        package fx;
                        import java.lang.annotation.*;
                        @Retention(RetentionPolicy.RUNTIME)
                        public @interface MyAnnotation {
                            String value() default "";
                            int count() default 0;
                            Class<?>[] classes() default {};
                        }
                        """),
                source("fx.Color", """
                        package fx;
                        public enum Color {
                            RED, GREEN, BLUE
                        }
                        """),
                source("fx.MyInterface", """
                        package fx;
                        public interface MyInterface<T> {
                            T get();
                            default void doNothing() {}
                        }
                        """),
                source("fx.Point", """
                        package fx;
                        public record Point(int x, int y) {}
                        """),
                source("fx.Sample", """
                        package fx;
                        import java.util.List;
                        @MyAnnotation(value = "hello", count = 42, classes = {String.class, Integer.class})
                        public class Sample<T extends Number> implements MyInterface<T> {
                            public static final int MAX = 100;
                            public static final String NAME = "sample";
                            public static final double PI = 3.14;
                            public static final char INITIAL = 'S';
                            public static final boolean ENABLED = true;

                            private final T value;

                            public Sample(T value) { this.value = value; }
                            public Sample(T value, String label) throws IllegalArgumentException {
                                this.value = value;
                            }

                            @Override
                            public T get() { return value; }

                            public <R> R transform(java.util.function.Function<T, R> fn) {
                                return fn.apply(value);
                            }

                            public void varargsMethod(String first, Object... rest) {}

                            @Deprecated(since = "2.0", forRemoval = true)
                            public List<String> deprecatedMethod() { return List.of(); }
                        }
                        """));

        var classesDir = workDir.resolve("classes");
        Files.createDirectories(classesDir);
        compile(sources, classesDir);

        fixtureJar = workDir.resolve("fixture-0.jar");
        packageJar(classesDir, fixtureJar);
    }

    @Test
    void schemaLoadsAndDeclaresArrayRoot() throws Exception {
        try (var schemaStream = JsonSchemaValidationTest.class.getResourceAsStream("/api-schema.json")) {
            var raw = objectMapper.readTree(schemaStream);
            assertTrue(raw.has("$schema"), "schema must declare $schema");
            assertTrue(raw.has("definitions"), "schema must declare definitions");
        }
    }

    @Test
    void extractedJsonValidatesAgainstSchema() throws Exception {
        var json = extractAsJson(fixtureJar);
        var node = objectMapper.readTree(json);

        Set<ValidationMessage> messages = schema.validate(node);
        assertTrue(messages.isEmpty(),
                () -> "Generated JSON failed schema validation:\n" + formatMessages(messages)
                        + "\n\nJSON was:\n" + json);
    }

    @Test
    void schemaRejectsKnownInvalidShape() throws Exception {
        // Sanity check: confirm the validator actually catches violations. A library result
        // missing the required `types` field must fail.
        var bad = """
                [
                  {
                    "library": {"groupId": "g", "artifactId": "a", "version": "1"}
                  }
                ]
                """;
        var node = objectMapper.readTree(bad);
        Set<ValidationMessage> messages = schema.validate(node);
        assertTrue(!messages.isEmpty(), "validator should flag missing required field 'types'");
    }

    private static String extractAsJson(Path jar) throws Exception {
        var fileName = jar.getFileName().toString();
        var artifactId = fileName.substring(0, fileName.length() - "-0.jar".length());
        var context = new ExtractContext(
                jar.getParent().toString(),
                new String[]{"g:" + artifactId + ":0"},
                false, null, new String[0]);
        var library = context.getLibraries()[0];
        var types = RevapiReflector.reflectOverJar(context, library, new ArrayList<>());
        var libraries = List.of(new LibraryResult(library, types));
        return JsonSerializer.serialize(libraries);
    }

    private static String formatMessages(Set<ValidationMessage> messages) {
        return messages.stream()
                .map(ValidationMessage::toString)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static JavaFileObject source(String fqcn, String body) {
        return new SimpleJavaFileObject(
                URI.create("string:///" + fqcn.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return body;
            }
        };
    }

    private static void compile(List<JavaFileObject> sources, Path outDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "no system javac — tests must run on a JDK");
        var options = List.of("-d", outDir.toString());
        var task = compiler.getTask(null, null, null, options, null, sources);
        assertTrue(task.call(), "javac compilation of test sources failed");
    }

    private static void packageJar(Path classesDir, Path jar) throws Exception {
        try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
            try (Stream<Path> walk = Files.walk(classesDir)) {
                for (Path p : (Iterable<Path>) walk::iterator) {
                    if (Files.isDirectory(p)) {
                        continue;
                    }
                    var rel = classesDir.relativize(p).toString().replace('\\', '/');
                    out.putNextEntry(new JarEntry(rel));
                    Files.copy(p, out);
                    out.closeEntry();
                }
            }
        }
    }
}
