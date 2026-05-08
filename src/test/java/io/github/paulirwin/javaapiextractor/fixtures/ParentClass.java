package io.github.paulirwin.javaapiextractor.fixtures;

public abstract class ParentClass {
    public void inheritedMethod() {
        // Declared on parent: when reflecting over PublicClass, this must NOT appear.
    }
}
