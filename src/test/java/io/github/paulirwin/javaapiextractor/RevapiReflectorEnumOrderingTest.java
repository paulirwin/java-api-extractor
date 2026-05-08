package io.github.paulirwin.javaapiextractor;

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
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of enum constant ordering (issue #8). Enum constants must keep
 * their source declaration order so {@code ordinal()} values and serialization remain
 * stable; only non-constant fields are sorted alphabetically.
 */
class RevapiReflectorEnumOrderingTest {

    @TempDir
    static Path workDir;
    static Path libraryJar;

    @BeforeAll
    static void compileAndPackage() throws Exception {
        // Source order is intentionally non-alphabetical so the test would fail under
        // the previous "sort all fields alphabetically" behavior.
        var sources = List.of(
                source("e.Color", """
                        package e;
                        public enum Color {
                            RED,
                            GREEN,
                            BLUE,
                            ALPHA;
                        }
                        """),
                source("e.Mixed", """
                        package e;
                        public enum Mixed {
                            ZULU,
                            ALPHA,
                            MIKE;

                            public static final int ZEBRA = 1;
                            public static final int ALPINE = 2;
                        }
                        """),
                source("e.Plain", """
                        package e;
                        public class Plain {
                            public static final int X = 1;
                        }
                        """));

        var classesDir = workDir.resolve("classes");
        Files.createDirectories(classesDir);
        compile(sources, classesDir);

        libraryJar = workDir.resolve("enums.jar");
        packageJar(classesDir, libraryJar);
    }

    @Test
    void enumConstantsKeepSourceOrder() throws Exception {
        var type = reflect(libraryJar, "e.Color");
        var names = type.enumConstants().stream().map(EnumConstantMetadata::name).toList();
        assertEquals(List.of("RED", "GREEN", "BLUE", "ALPHA"), names,
                "enum constants must preserve source order to keep ordinal() stable");
    }

    @Test
    void enumConstantsAndPlainFieldsLiveInSeparateLists() throws Exception {
        var type = reflect(libraryJar, "e.Mixed");

        var constantNames = type.enumConstants().stream().map(EnumConstantMetadata::name).toList();
        assertEquals(List.of("ZULU", "ALPHA", "MIKE"), constantNames,
                "enumConstants holds enum constants in source order");

        var fieldNames = type.fields().stream().map(FieldMetadata::name).toList();
        assertEquals(List.of("ALPINE", "ZEBRA"), fieldNames,
                "fields holds plain fields only, sorted alphabetically");
    }

    @Test
    void nonEnumTypesHaveEmptyEnumConstants() throws Exception {
        var type = reflect(libraryJar, "e.Plain");
        assertTrue(type.enumConstants().isEmpty(),
                "non-enum types must surface an empty enumConstants list");
        var fieldNames = type.fields().stream().map(FieldMetadata::name).toList();
        assertEquals(List.of("X"), fieldNames, "plain fields still appear in fields()");
    }

    private static TypeMetadata reflect(Path jar, String typeName) throws Exception {
        var fileName = jar.getFileName().toString();
        var artifactId = fileName.substring(0, fileName.length() - ".jar".length());
        var version = "0";
        var canonical = jar.resolveSibling("%s-%s.jar".formatted(artifactId, version));
        if (!Files.exists(canonical)) {
            Files.copy(jar, canonical);
        }
        var context = new ExtractContext(
                canonical.getParent().toString(),
                new String[]{"g:" + artifactId + ":" + version},
                false, null, new String[0]);
        var library = context.getLibraries()[0];
        var types = RevapiReflector.reflectOverJar(context, library, new ArrayList<>());
        return types.stream()
                .filter(t -> typeName.equals(t.fullName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("type not found: " + typeName));
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
