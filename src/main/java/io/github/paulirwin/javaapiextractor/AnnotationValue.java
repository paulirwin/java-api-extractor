package io.github.paulirwin.javaapiextractor;

import java.util.List;

/**
 * One value supplied to an annotation element. Annotation arguments are recursive — an
 * array element or annotation-typed value is itself an {@code AnnotationValue}.
 * <p>
 * Each variant exposes a {@code kind} string as its first record component so the
 * serialized JSON carries the discriminator without any framework-specific annotations:
 * {@code {"kind": "...", "value": ...}}. Consumers in non-Java languages can dispatch on
 * {@code kind} alone.
 */
public sealed interface AnnotationValue extends Comparable<AnnotationValue>
        permits AnnotationValue.BooleanValue,
                AnnotationValue.ByteValue,
                AnnotationValue.ShortValue,
                AnnotationValue.IntValue,
                AnnotationValue.LongValue,
                AnnotationValue.FloatValue,
                AnnotationValue.DoubleValue,
                AnnotationValue.CharValue,
                AnnotationValue.StringValue,
                AnnotationValue.ClassValue,
                AnnotationValue.EnumValue,
                AnnotationValue.AnnotationValueRef,
                AnnotationValue.ArrayValue {

    String kind();

    @Override
    default int compareTo(AnnotationValue other) {
        int c = this.kind().compareTo(other.kind());
        if (c != 0) {
            return c;
        }
        return compareSameKind(other);
    }

    /**
     * Compare two values known to share the same {@link #kind()}. Implementations may
     * cast {@code other} to their own type.
     */
    int compareSameKind(AnnotationValue other);

    record BooleanValue(String kind, boolean value) implements AnnotationValue {
        public BooleanValue(boolean value) { this("boolean", value); }
        @Override public int compareSameKind(AnnotationValue other) {
            return Boolean.compare(value, ((BooleanValue) other).value);
        }
    }

    record ByteValue(String kind, byte value) implements AnnotationValue {
        public ByteValue(byte value) { this("byte", value); }
        @Override public int compareSameKind(AnnotationValue other) {
            return Byte.compare(value, ((ByteValue) other).value);
        }
    }

    record ShortValue(String kind, short value) implements AnnotationValue {
        public ShortValue(short value) { this("short", value); }
        @Override public int compareSameKind(AnnotationValue other) {
            return Short.compare(value, ((ShortValue) other).value);
        }
    }

    record IntValue(String kind, int value) implements AnnotationValue {
        public IntValue(int value) { this("int", value); }
        @Override public int compareSameKind(AnnotationValue other) {
            return Integer.compare(value, ((IntValue) other).value);
        }
    }

    record LongValue(String kind, long value) implements AnnotationValue {
        public LongValue(long value) { this("long", value); }
        @Override public int compareSameKind(AnnotationValue other) {
            return Long.compare(value, ((LongValue) other).value);
        }
    }

    record FloatValue(String kind, float value) implements AnnotationValue {
        public FloatValue(float value) { this("float", value); }
        @Override public int compareSameKind(AnnotationValue other) {
            return Float.compare(value, ((FloatValue) other).value);
        }
    }

    record DoubleValue(String kind, double value) implements AnnotationValue {
        public DoubleValue(double value) { this("double", value); }
        @Override public int compareSameKind(AnnotationValue other) {
            return Double.compare(value, ((DoubleValue) other).value);
        }
    }

    /** Char values render as a 1-character JSON string, not a numeric code point. */
    record CharValue(String kind, String value) implements AnnotationValue {
        public CharValue(char value) { this("char", String.valueOf(value)); }
        @Override public int compareSameKind(AnnotationValue other) {
            return value.compareTo(((CharValue) other).value);
        }
    }

    record StringValue(String kind, String value) implements AnnotationValue {
        public StringValue(String value) { this("string", value); }
        @Override public int compareSameKind(AnnotationValue other) {
            return value.compareTo(((StringValue) other).value);
        }
    }

    /** {@code value} is the binary name of the referenced class, e.g. {@code Outer$Inner}. */
    record ClassValue(String kind, String value) implements AnnotationValue {
        public ClassValue(String value) { this("class", value); }
        @Override public int compareSameKind(AnnotationValue other) {
            return value.compareTo(((ClassValue) other).value);
        }
    }

    record EnumValue(String kind, String type, String value) implements AnnotationValue {
        public EnumValue(String type, String value) { this("enum", type, value); }
        @Override public int compareSameKind(AnnotationValue other) {
            var o = (EnumValue) other;
            int c = type.compareTo(o.type);
            if (c != 0) {
                return c;
            }
            return value.compareTo(o.value);
        }
    }

    /**
     * A nested annotation, e.g. {@code @Outer(inner = @Inner(...))}. Wraps a full
     * {@link AnnotationMetadata} so the inner annotation's own arguments are preserved.
     */
    record AnnotationValueRef(String kind, AnnotationMetadata value) implements AnnotationValue {
        public AnnotationValueRef(AnnotationMetadata value) { this("annotation", value); }
        @Override public int compareSameKind(AnnotationValue other) {
            return value.compareTo(((AnnotationValueRef) other).value);
        }
    }

    /**
     * Array values preserve declaration order — array element order is part of the
     * annotation's contract (e.g. {@code @On({A.class, B.class})}).
     */
    record ArrayValue(String kind, List<AnnotationValue> value) implements AnnotationValue {
        public ArrayValue(List<AnnotationValue> value) { this("array", List.copyOf(value)); }
        @Override public int compareSameKind(AnnotationValue other) {
            var a = value;
            var b = ((ArrayValue) other).value;
            int min = Math.min(a.size(), b.size());
            for (int i = 0; i < min; i++) {
                int c = a.get(i).compareTo(b.get(i));
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(a.size(), b.size());
        }
    }
}
