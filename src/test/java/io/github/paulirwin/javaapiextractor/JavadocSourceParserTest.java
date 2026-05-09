package io.github.paulirwin.javaapiextractor;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class JavadocSourceParserTest {

    @Test
    void returnsEmptyWhenSourcesJarIsNull() {
        var parser = JavadocSourceParser.fromSourcesJar(null);
        assertTrue(parser.isEmpty());
    }

    @Test
    void returnsEmptyWhenSourcesJarMissing() {
        var parser = JavadocSourceParser.fromSourcesJar(new File("/does/not/exist.jar"));
        assertTrue(parser.isEmpty());
    }

    @Test
    void indexesTopLevelClassJavadoc() throws IOException {
        var jar = makeSourcesJar("com/foo/Hello.java", """
                package com.foo;
                /** A friendly greeter. */
                public class Hello {
                }
                """);
        try {
            var parser = JavadocSourceParser.fromSourcesJar(jar);
            var entry = parser.lookup(JavadocSourceParser.typeKey("com.foo.Hello"));
            assertNotNull(entry);
            assertTrue(entry.rawText().contains("friendly greeter"));
        } finally {
            jar.delete();
        }
    }

    @Test
    void indexesMethodAndFieldJavadoc() throws IOException {
        var jar = makeSourcesJar("com/foo/Bar.java", """
                package com.foo;
                public class Bar {
                    /** count of widgets */
                    public int count;

                    /**
                     * Adds one widget.
                     * @param x the amount
                     * @return the new count
                     */
                    public int add(int x) { return 0; }
                }
                """);
        try {
            var parser = JavadocSourceParser.fromSourcesJar(jar);

            var field = parser.lookup(JavadocSourceParser.fieldKey("com.foo.Bar", "count"));
            assertNotNull(field);
            assertTrue(field.rawText().contains("count of widgets"));

            var method = parser.lookup(
                    JavadocSourceParser.methodKey("com.foo.Bar", "add", java.util.List.of("int")));
            assertNotNull(method);
            assertEquals("Adds one widget.", method.description());
            assertEquals("the new count", method.tags().get("return"));
            // paramDocs is positional; one parameter declared, so size==1 and slot 0 has the @param content
            assertEquals(1, method.paramDocs().size());
            assertEquals("the amount", method.paramDocs().get(0));
        } finally {
            jar.delete();
        }
    }

    @Test
    void indexesNestedTypesWithBinaryNameSeparator() throws IOException {
        var jar = makeSourcesJar("com/foo/Outer.java", """
                package com.foo;
                public class Outer {
                    /** Inner class doc. */
                    public static class Inner {}
                }
                """);
        try {
            var parser = JavadocSourceParser.fromSourcesJar(jar);
            var entry = parser.lookup(JavadocSourceParser.typeKey("com.foo.Outer$Inner"));
            assertNotNull(entry);
            assertTrue(entry.rawText().contains("Inner class doc"));
        } finally {
            jar.delete();
        }
    }

    @Test
    void simpleTypeNameStripsPackagesAndGenerics() {
        assertEquals("String", JavadocSourceParser.simpleTypeName("java.lang.String"));
        assertEquals("List", JavadocSourceParser.simpleTypeName("java.util.List<java.lang.String>"));
        assertEquals("String[]", JavadocSourceParser.simpleTypeName("java.lang.String[]"));
        assertEquals("Inner", JavadocSourceParser.simpleTypeName("com.foo.Outer$Inner"));
        assertEquals("int", JavadocSourceParser.simpleTypeName("int"));
        assertEquals("int[]", JavadocSourceParser.simpleTypeName("int[]"));
    }

    private static File makeSourcesJar(String entryName, String content) throws IOException {
        Path tmp = Files.createTempFile("javadoc-test-", ".jar");
        try (var zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return tmp.toFile();
    }
}
