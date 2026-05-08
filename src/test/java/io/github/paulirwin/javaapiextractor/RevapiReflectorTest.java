package io.github.paulirwin.javaapiextractor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Revapi-based reflector. The full extraction path requires a JDK
 * (not just a JRE) at runtime because Revapi drives {@code javac}; that path is exercised
 * by the integration tests in {@link ExtractRunnerIT}. This file covers helpers that
 * don't need a live javac environment.
 */
class RevapiReflectorTest {

    @Nested
    class IsAnonymousClassBinaryName {
        @Test
        void plainTopLevelIsNotAnonymous() {
            assertFalse(RevapiReflector.isAnonymousClassBinaryName("com.example.Foo"));
        }

        @Test
        void namedNestedIsNotAnonymous() {
            assertFalse(RevapiReflector.isAnonymousClassBinaryName("com.example.Foo$Bar"));
            assertFalse(RevapiReflector.isAnonymousClassBinaryName("com.example.Foo$Bar$Baz"));
        }

        @Test
        void numericSegmentMarksAnonymous() {
            assertTrue(RevapiReflector.isAnonymousClassBinaryName("com.example.Foo$1"));
            assertTrue(RevapiReflector.isAnonymousClassBinaryName("com.example.Foo$2"));
        }

        @Test
        void numericSegmentInsideNestedChainMarksAnonymous() {
            assertTrue(RevapiReflector.isAnonymousClassBinaryName("com.example.Foo$Bar$1"));
            assertTrue(RevapiReflector.isAnonymousClassBinaryName("com.example.Foo$1$Inner"));
        }

        @Test
        void multiDigitNumericSegmentIsAnonymous() {
            assertTrue(RevapiReflector.isAnonymousClassBinaryName("com.example.Foo$42"));
        }

        @Test
        void packageOnlyIsNotAnonymous() {
            // No $ in the name — definitely not anonymous.
            assertFalse(RevapiReflector.isAnonymousClassBinaryName("com.example.foo.Bar"));
        }
    }
}
