package io.github.paulirwin.javaapiextractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final String[] ICU4J_60_1_LIBS = {
            "com.ibm.icu:icu4j:60.1"
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

    /**
     * End-to-end: ICU4j extraction must produce JSON that validates against the schema.
     * ICU4j includes many Javadoc examples with custom @stable tags, making it a good
     * test for comprehensive Javadoc extraction (see issue #11).
     */
    @Test
    void extractedICU4jJsonValidatesAgainstSchema() throws Exception {
        var context = new ExtractContext("download", ICU4J_60_1_LIBS, false, null, new String[0]);

        // Ensure the jar is on disk before Revapi tries to read it. The Lucene tests
        // above happen to download via getHash() first, which leaves the jar cached for
        // the schema-validation test that follows; this test has no such predecessor.
        for (var library : context.getLibraries()) {
            JarDownloader.downloadMavenDependency(context, library, context.isForce());
        }

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
                () -> "ICU4j API JSON failed schema validation:\n"
                        + messages.stream()
                                .map(ValidationMessage::toString)
                                .reduce((a, b) -> a + "\n" + b)
                                .orElse(""));
    }

    /**
     * End-to-end: the extracted Lucene JSON must contain Javadoc populated from the
     * {@code -sources.jar}. Without the sources-jar overlay, every javadoc field is null
     * because Revapi only feeds the binary jar to javac and bytecode doesn't carry
     * Javadoc — this test exists to catch a regression that would silently empty out
     * the documentation for every consumer.
     * <p>
     * Assertions are anchored on stable Lucene 4.8.1 declarations and on aggregate
     * coverage thresholds picked well below the actual numbers (~616/2308/683) so that
     * minor Lucene-Javadoc edits don't cause flakes.
     */
    @Test
    void extractedLuceneIncludesJavadoc() throws Exception {
        var context = new ExtractContext("download", LUCENE_4_8_1_LIBS, false, null, new String[0]);
        var libraries = RevapiReflector.reflectOverJars(context);

        // Find the lucene-core LibraryResult — order is sorted by coordinates, so it's not
        // necessarily index 0. (lucene-analyzers-common shares the same group/version.)
        LibraryResult core = libraries.stream()
                .filter(l -> "lucene-core".equals(l.library().artifactId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("lucene-core not in extraction output"));

        // 1) Type-level Javadoc: LucenePackage is a tiny, stable class whose Javadoc is
        //    just the class description — easy to assert without binding to specific
        //    wording that might shift across patch releases.
        TypeMetadata lucenePackage = findType(core, "org.apache.lucene.LucenePackage");
        assertNotNull(lucenePackage.javadoc(), "LucenePackage should have type-level Javadoc");
        assertNotNull(lucenePackage.javadoc().description());
        assertTrue(lucenePackage.javadoc().description().toLowerCase().contains("package"),
                () -> "LucenePackage description should mention 'package', got: "
                        + lucenePackage.javadoc().description());

        // 2) Method Javadoc with @param + @return: Analyzer.createComponents(String, Reader)
        //    is a stable abstract API on Lucene's Analyzer with three documented elements.
        TypeMetadata analyzer = findType(core, "org.apache.lucene.analysis.Analyzer");
        MethodMetadata createComponents = analyzer.methods().stream()
                .filter(m -> "createComponents".equals(m.name()) && m.parameters().size() == 2)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Analyzer.createComponents(String, Reader) not found"));

        assertNotNull(createComponents.javadoc(), "createComponents should have Javadoc");
        assertNotNull(createComponents.javadoc().description());
        assertNotNull(createComponents.javadoc().tags());
        assertTrue(createComponents.javadoc().tags().containsKey("param"),
                "createComponents should have @param tags");
        assertTrue(createComponents.javadoc().tags().containsKey("return"),
                "createComponents should have an @return tag");

        // 3) Parameter-level Javadoc: per-parameter docs must flow even though Lucene's
        //    binary jar doesn't preserve source parameter names (so the JSON shows them
        //    as arg0/arg1). The positional lookup is what makes this work — regression
        //    here would silently drop @param content.
        ParameterMetadata firstParam = createComponents.parameters().get(0);
        assertNotNull(firstParam.javadoc(),
                "First parameter of createComponents should have Javadoc from the @param tag");
        assertNotNull(firstParam.javadoc().description());
        assertFalse(firstParam.javadoc().description().isBlank(),
                "First parameter Javadoc should be non-blank");

        // 4) Aggregate coverage: thresholds intentionally well below observed values
        //    (616 types / 2308 methods / 683 params) so unrelated Javadoc edits don't
        //    flake the test, but high enough to catch a wholesale regression where the
        //    sources-jar parsing breaks and every value falls back to null.
        long typesWithDoc = core.types().stream()
                .filter(t -> t.javadoc() != null)
                .count();
        long methodsWithDoc = core.types().stream()
                .flatMap(t -> t.methods().stream())
                .filter(m -> m.javadoc() != null)
                .count();
        long paramsWithDoc = core.types().stream()
                .flatMap(t -> t.methods().stream())
                .flatMap(m -> m.parameters().stream())
                .filter(p -> p.javadoc() != null)
                .count();
        assertTrue(typesWithDoc > 300,
                () -> "Expected >300 types with Javadoc, got " + typesWithDoc);
        assertTrue(methodsWithDoc > 1000,
                () -> "Expected >1000 methods with Javadoc, got " + methodsWithDoc);
        assertTrue(paramsWithDoc > 300,
                () -> "Expected >300 parameters with Javadoc, got " + paramsWithDoc);
    }

    private static TypeMetadata findType(LibraryResult lib, String fullName) {
        return lib.types().stream()
                .filter(t -> fullName.equals(t.fullName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Type not found in extraction output: " + fullName));
    }
}
