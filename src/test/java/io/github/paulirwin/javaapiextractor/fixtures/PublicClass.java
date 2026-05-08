package io.github.paulirwin.javaapiextractor.fixtures;

import java.io.IOException;
import java.util.List;

/**
 * A hand-crafted public class exercising the full API surface the extractor should capture.
 */
@Deprecated
public class PublicClass<T extends Number> extends ParentClass implements PublicInterface {

    public static final String PUBLIC_CONST = "hello";
    protected int protectedField;
    private int privateField; // must be excluded
    static final int packagePrivateField = 42; // must be excluded

    public PublicClass() {
    }

    public PublicClass(String name) {
    }

    protected PublicClass(int x, int y) {
    }

    PublicClass(long ignored) {
        // package-private: must be excluded
    }

    private PublicClass(boolean ignored) {
        // private: must be excluded
    }

    public List<T> publicMethod(String arg) throws IOException {
        return null;
    }

    public <U> U genericMethod(U input) {
        return input;
    }

    public void varArgsMethod(String... args) {
    }

    protected void protectedMethod() {
    }

    void packagePrivateMethod() {
        // must be excluded
    }

    private void privateMethod() {
        // must be excluded
    }

    @Override
    @Deprecated
    public void interfaceMethod() {
    }

    public static class NestedPublic {
        public NestedPublic() {}
    }

    private static class NestedPrivate {
        // must be excluded at the type level
    }
}
