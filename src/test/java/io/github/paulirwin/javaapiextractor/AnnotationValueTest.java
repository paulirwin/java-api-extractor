package io.github.paulirwin.javaapiextractor;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationValueTest {

    @Nested
    class JsonShape {
        @Test
        void stringValueSerializesWithKindAndValue() {
            var v = new AnnotationValue.StringValue("hello");
            assertEquals("{\"kind\":\"string\",\"value\":\"hello\"}", JsonSerializer.serialize(v));
        }

        @Test
        void booleanValue() {
            assertEquals("{\"kind\":\"boolean\",\"value\":true}",
                    JsonSerializer.serialize(new AnnotationValue.BooleanValue(true)));
        }

        @Test
        void byteValue() {
            assertEquals("{\"kind\":\"byte\",\"value\":7}",
                    JsonSerializer.serialize(new AnnotationValue.ByteValue((byte) 7)));
        }

        @Test
        void shortValue() {
            assertEquals("{\"kind\":\"short\",\"value\":7}",
                    JsonSerializer.serialize(new AnnotationValue.ShortValue((short) 7)));
        }

        @Test
        void intValue() {
            assertEquals("{\"kind\":\"int\",\"value\":42}",
                    JsonSerializer.serialize(new AnnotationValue.IntValue(42)));
        }

        @Test
        void longValueDistinguishedFromInt() {
            // Important: int 1 vs long 1 must produce different JSON.
            var asInt = JsonSerializer.serialize(new AnnotationValue.IntValue(1));
            var asLong = JsonSerializer.serialize(new AnnotationValue.LongValue(1L));
            assertNotEquals(asInt, asLong);
            assertEquals("{\"kind\":\"long\",\"value\":1}", asLong);
        }

        @Test
        void floatValue() {
            assertEquals("{\"kind\":\"float\",\"value\":1.5}",
                    JsonSerializer.serialize(new AnnotationValue.FloatValue(1.5f)));
        }

        @Test
        void doubleValue() {
            assertEquals("{\"kind\":\"double\",\"value\":1.5}",
                    JsonSerializer.serialize(new AnnotationValue.DoubleValue(1.5)));
        }

        @Test
        void charValue() {
            assertEquals("{\"kind\":\"char\",\"value\":\"a\"}",
                    JsonSerializer.serialize(new AnnotationValue.CharValue('a')));
        }

        @Test
        void classValueCarriesBinaryName() {
            assertEquals("{\"kind\":\"class\",\"value\":\"java.lang.Exception\"}",
                    JsonSerializer.serialize(new AnnotationValue.ClassValue("java.lang.Exception")));
        }

        @Test
        void enumValueCarriesTypeAndConstant() {
            var v = new AnnotationValue.EnumValue("java.lang.annotation.RetentionPolicy", "RUNTIME");
            assertEquals(
                    "{\"kind\":\"enum\",\"type\":\"java.lang.annotation.RetentionPolicy\",\"value\":\"RUNTIME\"}",
                    JsonSerializer.serialize(v));
        }

        @Test
        void annotationValueWrapsAnnotationMetadata() {
            // String value passed as the only argument of a nested @PublicAnnotation("hi").
            var nested = new AnnotationMetadata(
                    "io.example.Marker",
                    List.of(new AnnotationArgument("value", new AnnotationValue.StringValue("hi"))));
            var v = new AnnotationValue.AnnotationValueRef(nested);
            assertEquals(
                    "{\"kind\":\"annotation\",\"value\":{\"type\":\"io.example.Marker\","
                            + "\"arguments\":[{\"name\":\"value\","
                            + "\"value\":{\"kind\":\"string\",\"value\":\"hi\"}}]}}",
                    JsonSerializer.serialize(v));
        }

        @Test
        void arrayValueWrapsElements() {
            var v = new AnnotationValue.ArrayValue(List.of(
                    new AnnotationValue.IntValue(1),
                    new AnnotationValue.IntValue(2)));
            assertEquals(
                    "{\"kind\":\"array\",\"value\":[{\"kind\":\"int\",\"value\":1},"
                            + "{\"kind\":\"int\",\"value\":2}]}",
                    JsonSerializer.serialize(v));
        }
    }

    @Nested
    class CompareTo {
        @Test
        void differentKindsOrderByKindName() {
            // "boolean" < "int" alphabetically.
            var a = new AnnotationValue.BooleanValue(true);
            var b = new AnnotationValue.IntValue(0);
            assertTrue(a.compareTo(b) < 0);
        }

        @Test
        void sameKindOrdersByValue() {
            var a = new AnnotationValue.IntValue(1);
            var b = new AnnotationValue.IntValue(2);
            assertTrue(a.compareTo(b) < 0);
            assertEquals(0, a.compareTo(new AnnotationValue.IntValue(1)));
        }

        @Test
        void enumValueOrdersByTypeThenConstant() {
            var a = new AnnotationValue.EnumValue("pkg.E", "A");
            var b = new AnnotationValue.EnumValue("pkg.E", "B");
            var c = new AnnotationValue.EnumValue("pkg.F", "A");
            assertTrue(a.compareTo(b) < 0);
            assertTrue(b.compareTo(c) < 0);
        }

        @Test
        void arrayElementsCompareInOrder_withoutSorting() {
            // Array element order is significant: @On({A.class, B.class}) is not the
            // same as @On({B.class, A.class}) when the annotation processor cares.
            var ab = new AnnotationValue.ArrayValue(List.of(
                    new AnnotationValue.ClassValue("A"),
                    new AnnotationValue.ClassValue("B")));
            var ba = new AnnotationValue.ArrayValue(List.of(
                    new AnnotationValue.ClassValue("B"),
                    new AnnotationValue.ClassValue("A")));
            assertTrue(ab.compareTo(ba) < 0);
        }
    }

    @Nested
    class Argument {
        @Test
        void serializesNameThenValue() {
            var arg = new AnnotationArgument("since", new AnnotationValue.StringValue("9"));
            assertEquals("{\"name\":\"since\",\"value\":{\"kind\":\"string\",\"value\":\"9\"}}",
                    JsonSerializer.serialize(arg));
        }

        @Test
        void sortsByName() {
            var a = new AnnotationArgument("aaa", new AnnotationValue.IntValue(0));
            var b = new AnnotationArgument("zzz", new AnnotationValue.IntValue(0));
            assertTrue(a.compareTo(b) < 0);
        }
    }
}
