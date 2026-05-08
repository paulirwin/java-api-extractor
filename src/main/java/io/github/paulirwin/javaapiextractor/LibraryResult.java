package io.github.paulirwin.javaapiextractor;

import java.util.List;

public record LibraryResult(MavenCoordinates library, List<TypeMetadata> types)
        implements Comparable<LibraryResult> {
    public LibraryResult {
        types = List.copyOf(types);
    }

    @Override
    public int compareTo(LibraryResult other) {
        return this.library.compareTo(other.library);
    }
}
