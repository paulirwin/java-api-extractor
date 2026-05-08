package io.github.paulirwin.javaapiextractor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExtractContextTest {

    @Test
    void parsesLibrariesAsMavenCoordinates() {
        var context = new ExtractContext(
                "download",
                new String[]{"org.apache.lucene:lucene-core:4.8.1", "org.apache.lucene:lucene-analyzers-common:4.8.1"},
                false,
                null,
                new String[0]);

        var libs = context.getLibraries();
        assertEquals(2, libs.length);
        assertEquals(new MavenCoordinates("org.apache.lucene", "lucene-core", "4.8.1"), libs[0]);
        assertEquals(new MavenCoordinates("org.apache.lucene", "lucene-analyzers-common", "4.8.1"), libs[1]);
    }

    @Test
    void parsesDependenciesAsMavenCoordinates() {
        var context = new ExtractContext(
                "download",
                new String[]{"org.apache.lucene:lucene-core:4.8.1"},
                false,
                null,
                new String[]{"com.ibm.icu:icu4j:54.1"});

        var deps = context.getDependencies();
        assertEquals(1, deps.length);
        assertEquals(new MavenCoordinates("com.ibm.icu", "icu4j", "54.1"), deps[0]);
    }

    @Test
    void isStandardOutput_trueWhenOutputFileNull() {
        var context = new ExtractContext("download", new String[]{"g:a:v"}, false, null, new String[0]);
        assertTrue(context.isStandardOutput());
    }

    @Test
    void isStandardOutput_falseWhenOutputFileProvided() {
        var context = new ExtractContext("download", new String[]{"g:a:v"}, false, "out.json", new String[0]);
        assertFalse(context.isStandardOutput());
        assertEquals("out.json", context.getOutputFile());
    }

    @Test
    void emptyDependenciesArrayIsHandled() {
        var context = new ExtractContext("download", new String[]{"g:a:v"}, false, null, new String[0]);
        assertEquals(0, context.getDependencies().length);
    }

    @Test
    void downloadsDirAndForceAreExposed() {
        var context = new ExtractContext("my-dir", new String[]{"g:a:v"}, true, null, new String[0]);
        assertEquals("my-dir", context.getDownloadsDir());
        assertTrue(context.isForce());
    }

    @Test
    void rejectsMalformedCoordinateWithTooFewParts() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new ExtractContext("download", new String[]{"no-colons-here"}, false, null, new String[0]));
        assertTrue(ex.getMessage().contains("no-colons-here"));
    }

    @Test
    void rejectsMalformedCoordinateWithTooManyParts() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExtractContext("download", new String[]{"a:b:c:d"}, false, null, new String[0]));
    }

    @Test
    void defaultsStrictFalseAndVerifyChecksumTrue() {
        var context = new ExtractContext("download", new String[]{"g:a:v"}, false, null, new String[0]);
        assertFalse(context.isStrict());
        assertTrue(context.isVerifyChecksum());
    }

    @Test
    void extendedConstructorExposesStrictAndVerifyChecksum() {
        var context = new ExtractContext(
                "download", new String[]{"g:a:v"}, false, null, new String[0], true, false);
        assertTrue(context.isStrict());
        assertFalse(context.isVerifyChecksum());
    }
}
