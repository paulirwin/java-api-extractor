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
 * End-to-end coverage of annotation-argument extraction: compile a tiny source set
 * with javac at test time, package it as a jar, and drive {@link RevapiReflector}
 * against it. Covers every {@link AnnotationValue} kind in one pass.
 */
class RevapiReflectorAnnotationArgumentsTest {

    @TempDir
    static Path workDir;
    static Path libraryJar;

    @BeforeAll
    static void compileAndPackage() throws Exception {
        var sources = List.of(
                source("p.Color", """
                        package p;
                        public enum Color { RED, BLUE }
                        """),
                source("p.Marker", """
                        package p;
                        public @interface Marker {
                            String label() default "";
                        }
                        """),
                source("p.Outer", """
                        package p;
                        import java.lang.annotation.*;
                        public @interface Outer {
                            String name() default "";
                            int count() default 0;
                            long big() default 0L;
                            boolean flag() default false;
                            byte b() default 0;
                            short s() default 0;
                            float f() default 0f;
                            double d() default 0d;
                            char ch() default ' ';
                            Class<?> cls() default Object.class;
                            Color color() default Color.RED;
                            Marker nested() default @Marker;
                            Class<?>[] classes() default {};
                        }
                        """),
                source("p.Subject", """
                        package p;
                        @Outer(
                            name = "hi",
                            count = 7,
                            big = 9876543210L,
                            flag = true,
                            b = 1,
                            s = 2,
                            f = 1.5f,
                            d = 2.5,
                            ch = 'x',
                            cls = String.class,
                            color = Color.BLUE,
                            nested = @Marker(label = "inside"),
                            classes = { Integer.class, Long.class }
                        )
                        public class Subject {
                        }
                        """));

        var classesDir = workDir.resolve("classes");
        Files.createDirectories(classesDir);
        compile(sources, classesDir);

        libraryJar = workDir.resolve("library.jar");
        packageJar(classesDir, libraryJar);
    }

    @Test
    void extractsEveryAnnotationValueKind() throws Exception {
        var subject = reflect(libraryJar, "p.Subject");

        // Find @Outer on the type.
        var outer = subject.annotations().stream()
                .filter(a -> "p.Outer".equals(a.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing @Outer annotation"));

        var args = outer.arguments();
        // Arguments must be sorted alphabetically by name.
        var names = args.stream().map(AnnotationArgument::name).toList();
        var sorted = names.stream().sorted().toList();
        assertEquals(sorted, names, "arguments should be sorted by name");

        // Spot-check each kind.
        assertValue(args, "name", new AnnotationValue.StringValue("hi"));
        assertValue(args, "count", new AnnotationValue.IntValue(7));
        assertValue(args, "big", new AnnotationValue.LongValue(9876543210L));
        assertValue(args, "flag", new AnnotationValue.BooleanValue(true));
        assertValue(args, "b", new AnnotationValue.ByteValue((byte) 1));
        assertValue(args, "s", new AnnotationValue.ShortValue((short) 2));
        assertValue(args, "f", new AnnotationValue.FloatValue(1.5f));
        assertValue(args, "d", new AnnotationValue.DoubleValue(2.5));
        assertValue(args, "ch", new AnnotationValue.CharValue('x'));
        assertValue(args, "cls", new AnnotationValue.ClassValue("java.lang.String"));
        assertValue(args, "color", new AnnotationValue.EnumValue("p.Color", "BLUE"));

        // Nested annotation: @Marker(label = "inside")
        var nested = (AnnotationValue.AnnotationValueRef) findArg(args, "nested").value();
        assertEquals("p.Marker", nested.value().type());
        assertEquals(1, nested.value().arguments().size());
        assertValue(nested.value().arguments(), "label", new AnnotationValue.StringValue("inside"));

        // Array of classes — order preserved.
        var arr = (AnnotationValue.ArrayValue) findArg(args, "classes").value();
        assertEquals(List.of(
                new AnnotationValue.ClassValue("java.lang.Integer"),
                new AnnotationValue.ClassValue("java.lang.Long")), arr.value());
    }

    @Test
    void omitsDefaultValuesNotExplicitlySet() throws Exception {
        // Subject only sets some of @Outer's elements explicitly; the rest have defaults
        // declared on @Outer. Compile a fresh subject that uses *only* one element and
        // confirm the others are not present.
        var sources = List.of(
                source("q.Marker2", """
                        package q;
                        public @interface Marker2 {
                            String a() default "x";
                            String b() default "y";
                        }
                        """),
                source("q.Subject2", """
                        package q;
                        @Marker2(a = "explicit")
                        public class Subject2 {}
                        """));

        var classesDir = workDir.resolve("classes2");
        Files.createDirectories(classesDir);
        compile(sources, classesDir);
        var jar = workDir.resolve("library2.jar");
        packageJar(classesDir, jar);

        var subject = reflect(jar, "q.Subject2");
        var marker = subject.annotations().stream()
                .filter(a -> "q.Marker2".equals(a.type()))
                .findFirst()
                .orElseThrow();

        // Only the explicitly-set element should appear.
        assertEquals(1, marker.arguments().size(), "expected only explicit values, got " + marker.arguments());
        assertEquals("a", marker.arguments().get(0).name());
        assertValue(marker.arguments(), "a", new AnnotationValue.StringValue("explicit"));
    }

    private static void assertValue(List<AnnotationArgument> args, String name, AnnotationValue expected) {
        var actual = findArg(args, name).value();
        assertEquals(expected, actual, "value for " + name);
    }

    private static AnnotationArgument findArg(List<AnnotationArgument> args, String name) {
        return args.stream()
                .filter(a -> name.equals(a.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing argument: " + name));
    }

    private static TypeMetadata reflect(Path jar, String typeName) throws Exception {
        // Use the parent of the jar as the downloads dir and fake a maven coordinate
        // pointing at the jar's filename.
        var fileName = jar.getFileName().toString();
        // getJarName() builds "<artifactId>-<version>.jar"; pick coords that produce it.
        var artifactAndVersion = fileName.substring(0, fileName.length() - ".jar".length());
        // Split on the last '-': artifact="library", version="" (we'll use a simple form).
        var artifactId = artifactAndVersion;
        var version = "0";
        var jarName = "%s-%s.jar".formatted(artifactId, version);
        var renamed = jar.resolveSibling(jarName);
        if (!renamed.equals(jar)) {
            Files.move(jar, renamed);
        }
        var context = new ExtractContext(
                renamed.getParent().toString(),
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
