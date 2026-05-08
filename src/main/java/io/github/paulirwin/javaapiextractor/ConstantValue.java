package io.github.paulirwin.javaapiextractor;

/**
 * The compile-time constant value of a {@code public static final} field, per JLS
 * §15.28. Only the primitive types and {@code String} can carry a constant value, so
 * this hierarchy is intentionally narrower than {@link AnnotationValue}.
 * <p>
 * Like {@link AnnotationValue}, each variant exposes a {@code kind} discriminator as
 * its first record component so the JSON shape is {@code {"kind": "...", "value": ...}}
 * and consumers in non-Java languages can dispatch on {@code kind} alone.
 */
public sealed interface ConstantValue extends Comparable<ConstantValue>
        permits ConstantValue.BooleanValue,
                ConstantValue.ByteValue,
                ConstantValue.ShortValue,
                ConstantValue.IntValue,
                ConstantValue.LongValue,
                ConstantValue.FloatValue,
                ConstantValue.DoubleValue,
                ConstantValue.CharValue,
                ConstantValue.StringValue {

    String kind();

    @Override
    default int compareTo(ConstantValue other) {
        int c = this.kind().compareTo(other.kind());
        if (c != 0) {
            return c;
        }
        return compareSameKind(other);
    }

    int compareSameKind(ConstantValue other);

    record BooleanValue(String kind, boolean value) implements ConstantValue {
        public BooleanValue(boolean value) { this("boolean", value); }
        @Override public int compareSameKind(ConstantValue other) {
            return Boolean.compare(value, ((BooleanValue) other).value);
        }
    }

    record ByteValue(String kind, byte value) implements ConstantValue {
        public ByteValue(byte value) { this("byte", value); }
        @Override public int compareSameKind(ConstantValue other) {
            return Byte.compare(value, ((ByteValue) other).value);
        }
    }

    record ShortValue(String kind, short value) implements ConstantValue {
        public ShortValue(short value) { this("short", value); }
        @Override public int compareSameKind(ConstantValue other) {
            return Short.compare(value, ((ShortValue) other).value);
        }
    }

    record IntValue(String kind, int value) implements ConstantValue {
        public IntValue(int value) { this("int", value); }
        @Override public int compareSameKind(ConstantValue other) {
            return Integer.compare(value, ((IntValue) other).value);
        }
    }

    record LongValue(String kind, long value) implements ConstantValue {
        public LongValue(long value) { this("long", value); }
        @Override public int compareSameKind(ConstantValue other) {
            return Long.compare(value, ((LongValue) other).value);
        }
    }

    record FloatValue(String kind, float value) implements ConstantValue {
        public FloatValue(float value) { this("float", value); }
        @Override public int compareSameKind(ConstantValue other) {
            return Float.compare(value, ((FloatValue) other).value);
        }
    }

    record DoubleValue(String kind, double value) implements ConstantValue {
        public DoubleValue(double value) { this("double", value); }
        @Override public int compareSameKind(ConstantValue other) {
            return Double.compare(value, ((DoubleValue) other).value);
        }
    }

    /** Char values render as a 1-character JSON string, not a numeric code point. */
    record CharValue(String kind, String value) implements ConstantValue {
        public CharValue(char value) { this("char", String.valueOf(value)); }
        @Override public int compareSameKind(ConstantValue other) {
            return value.compareTo(((CharValue) other).value);
        }
    }

    record StringValue(String kind, String value) implements ConstantValue {
        public StringValue(String value) { this("string", value); }
        @Override public int compareSameKind(ConstantValue other) {
            return value.compareTo(((StringValue) other).value);
        }
    }
}
