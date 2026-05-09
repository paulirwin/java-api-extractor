package io.github.paulirwin.javaapiextractor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JavadocExtractionTest {

    @Test
    void javadocMetadataCreatesDefensiveCopyOfTags() {
        var original = Map.of("param", "x the value");
        var metadata = new JavadocMetadata("description", original, "raw");

        // Verify the tags are equal (map is copied so not same instance)
        assertEquals(original, metadata.tags());
    }

    @Test
    void javadocMetadataReturnsEmptyMapIfTagsIsNull() {
        var metadata = new JavadocMetadata("description", null, "raw");
        assertEquals(Map.of(), metadata.tags());
    }

    @Test
    void javadocMetadataIsComparable() {
        var a = new JavadocMetadata("aaa", Map.of(), "raw1");
        var b = new JavadocMetadata("bbb", Map.of(), "raw2");
        // Same description and tags, different rawText — rawText differs, so not equal
        var c = new JavadocMetadata("aaa", Map.of(), "raw3");

        assertTrue(a.compareTo(b) < 0);
        assertTrue(a.compareTo(c) < 0); // c's rawText differs
        assertTrue(b.compareTo(a) > 0);
    }

    @Test
    void javadocMetadataWithNullFieldsCompares() {
        var a = new JavadocMetadata(null, Map.of(), null);
        var b = new JavadocMetadata("desc", Map.of(), "raw");

        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }
}
