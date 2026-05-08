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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage of {@code public static final} compile-time constant capture
 * (issue #7). Each primitive plus String must round-trip through the extractor as the
 * matching {@link ConstantValue} variant. Non-final fields, non-primitive non-String
 * fields, and finals whose initializer isn't a JLS §15.28 constant must surface a null
 * {@code constantValue}.
 */
class RevapiReflectorFieldConstantsTest {

    @TempDir
    static Path workDir;
    static Path libraryJar;

    @BeforeAll
    static void compileAndPackage() throws Exception {
        var sources = List.of(
                source("c.Constants", """
                        package c;
                        public class Constants {
                            public static final boolean BOOL = true;
                            public static final byte BYTE = 7;
                            public static final short SHORT = 13;
                            public static final int INT = 42;
                            public static final long LONG = 9876543210L;
                            public static final float FLOAT = 1.5f;
                            public static final double DOUBLE = 2.5;
                            public static final char CHAR = 'x';
                            public static final String STRING = "hello";

                            // Final but not primitive/String — no compile-time constant value.
                            public static final Object OBJ = "not a constant";
                            // Final primitive but initialized from a non-constant expression.
                            public static final int RUNTIME = computed();
                            // Plain non-final field — never a constant.
                            public static int mutable = 1;
                            // Instance field — even if final, no compile-time constant value.
                            public final int instanceFinal = 1;

                            private static int computed() { return 1; }
                        }
                        """));

        var classesDir = workDir.resolve("classes");
        Files.createDirectories(classesDir);
        compile(sources, classesDir);

        libraryJar = workDir.resolve("constants.jar");
        packageJar(classesDir, libraryJar);
    }

    @Test
    void capturesPrimitiveAndStringConstants() throws Exception {
        var type = reflect(libraryJar, "c.Constants");

        assertConst(type, "BOOL", new ConstantValue.BooleanValue(true));
        assertConst(type, "BYTE", new ConstantValue.ByteValue((byte) 7));
        assertConst(type, "SHORT", new ConstantValue.ShortValue((short) 13));
        assertConst(type, "INT", new ConstantValue.IntValue(42));
        assertConst(type, "LONG", new ConstantValue.LongValue(9876543210L));
        assertConst(type, "FLOAT", new ConstantValue.FloatValue(1.5f));
        assertConst(type, "DOUBLE", new ConstantValue.DoubleValue(2.5));
        assertConst(type, "CHAR", new ConstantValue.CharValue('x'));
        assertConst(type, "STRING", new ConstantValue.StringValue("hello"));
    }

    @Test
    void omitsConstantValueForNonConstantFields() throws Exception {
        var type = reflect(libraryJar, "c.Constants");

        // final Object — not a JLS constant.
        assertNull(findField(type, "OBJ").constantValue(), "OBJ should have no constant value");
        // final int initialized from a method call — not a JLS constant.
        assertNull(findField(type, "RUNTIME").constantValue(), "RUNTIME should have no constant value");
        // non-final.
        assertNull(findField(type, "mutable").constantValue(), "mutable should have no constant value");
        // instance final — not static, no constant value semantics.
        assertNull(findField(type, "instanceFinal").constantValue(),
                "instanceFinal should have no constant value");
    }

    @Test
    void constantValueSerializesIntoFieldJson() throws Exception {
        var type = reflect(libraryJar, "c.Constants");
        var json = JsonSerializer.serialize(findField(type, "INT"));
        assertTrue(json.contains("\"name\":\"INT\""), () -> "missing field name in: " + json);
        assertTrue(json.contains("\"constantValue\":{\"kind\":\"int\",\"value\":42}"),
                () -> "missing or wrong constantValue in: " + json);

        var nullJson = JsonSerializer.serialize(findField(type, "mutable"));
        assertTrue(nullJson.contains("\"constantValue\":null"),
                () -> "non-constant field should serialize constantValue as null, got: " + nullJson);
    }

    private static void assertConst(TypeMetadata type, String name, ConstantValue expected) {
        var f = findField(type, name);
        assertEquals(expected, f.constantValue(), "constantValue for " + name);
    }

    private static FieldMetadata findField(TypeMetadata type, String name) {
        return type.fields().stream()
                .filter(f -> name.equals(f.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing field: " + name
                        + " (have: " + type.fields().stream().map(FieldMetadata::name).toList() + ")"));
    }

    private static TypeMetadata reflect(Path jar, String typeName) throws Exception {
        // Lay the jar down (idempotently) at the `<artifactId>-<version>.jar` path that
        // ExtractContext expects. Using a copy rather than a move keeps this safe to call
        // from multiple tests sharing the same source jar.
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
