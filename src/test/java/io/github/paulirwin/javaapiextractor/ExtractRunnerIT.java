package io.github.paulirwin.javaapiextractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests — require network access to Maven Central.
 * Run with {@code mvn verify}; failsafe picks these up by the {@code *IT} suffix.
 */
class ExtractRunnerIT {

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

    /**
     * End-to-end: real Lucene extraction must produce JSON that validates against the
     * shipped {@code api-schema.json}. Synthetic fixtures only cover what we think to add —
     * Lucene 4.8.1 has 1000+ types and exercises corner cases we don't.
     */
    @Test
    void extractedLuceneJsonValidatesAgainstSchema() throws Exception {
        var context = new ExtractContext("download", LUCENE_4_8_1_LIBS, false, null, new String[0]);

        var libraries = RevapiReflector.reflectOverJars(context);
        var json = JsonSerializer.serialize(libraries);

        var objectMapper = new ObjectMapper();
        var node = objectMapper.readTree(json);

        JsonSchema schema;
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        try (var schemaStream = ExtractRunnerIT.class.getResourceAsStream("/api-schema.json")) {
            assertNotNull(schemaStream, "Schema file not found in resources");
            schema = factory.getSchema(schemaStream);
        }

        Set<ValidationMessage> messages = schema.validate(node);
        assertTrue(messages.isEmpty(),
                () -> "Lucene API JSON failed schema validation:\n"
                        + messages.stream()
                                .map(ValidationMessage::toString)
                                .reduce((a, b) -> a + "\n" + b)
                                .orElse(""));
    }
}
