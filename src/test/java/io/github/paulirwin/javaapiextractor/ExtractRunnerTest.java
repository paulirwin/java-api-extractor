package io.github.paulirwin.javaapiextractor;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests — require network access to Maven Central.
 * Run with: {@code mvn -Pintegration-tests test}
 */
@Tag("integration")
class ExtractRunnerTest {

    private static final String[] LUCENE_4_8_1_LIBS = {
            "org.apache.lucene:lucene-core:4.8.1",
            "org.apache.lucene:lucene-analyzers-common:4.8.1"
    };

    @Test
    void testHashIsStable() throws Exception {
        var context1 = new ExtractContext("download", LUCENE_4_8_1_LIBS, false, null, new String[0]);
        var hash1 = ExtractRunner.getHash(context1);
        var context2 = new ExtractContext("download", LUCENE_4_8_1_LIBS, false, null, new String[0]);
        var hash2 = ExtractRunner.getHash(context2);
        assertEquals(hash1, hash2);
    }

    @Test
    void testHashIsProducedAsHexSha256() throws Exception {
        var context = new ExtractContext("download", LUCENE_4_8_1_LIBS, false, null, new String[0]);
        var hash = ExtractRunner.getHash(context);

        // SHA-256 hex is 64 chars of [0-9a-f]
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }
}
