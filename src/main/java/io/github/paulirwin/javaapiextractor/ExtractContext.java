package io.github.paulirwin.javaapiextractor;

import java.util.stream.Stream;

public class ExtractContext {
    private final String downloadsDir;
    private final MavenCoordinates[] libraries;
    private final boolean force;
    private final String outputFile;
    private final MavenCoordinates[] dependencies;
    private final boolean strict;
    private final boolean verifyChecksum;
    private final boolean stableParameterNames;

    public ExtractContext(String downloadsDir,
                          String[] libraryNames,
                          boolean force,
                          String outputFile,
                          String[] dependencies) {
        this(downloadsDir, libraryNames, force, outputFile, dependencies, false, true, false);
    }

    public ExtractContext(String downloadsDir,
                          String[] libraryNames,
                          boolean force,
                          String outputFile,
                          String[] dependencies,
                          boolean strict,
                          boolean verifyChecksum) {
        this(downloadsDir, libraryNames, force, outputFile, dependencies,
                strict, verifyChecksum, false);
    }

    public ExtractContext(String downloadsDir,
                          String[] libraryNames,
                          boolean force,
                          String outputFile,
                          String[] dependencies,
                          boolean strict,
                          boolean verifyChecksum,
                          boolean stableParameterNames) {
        this.downloadsDir = downloadsDir;
        this.libraries = Stream.of(libraryNames)
                .map(ExtractContext::parseCoordinates)
                .toArray(MavenCoordinates[]::new);
        this.force = force;
        this.outputFile = outputFile;
        this.dependencies = Stream.of(dependencies)
                .map(ExtractContext::parseCoordinates)
                .toArray(MavenCoordinates[]::new);
        this.strict = strict;
        this.verifyChecksum = verifyChecksum;
        this.stableParameterNames = stableParameterNames;
    }

    private static MavenCoordinates parseCoordinates(String coord) {
        var parts = coord.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Maven coordinate must be groupId:artifactId:version, got: " + coord);
        }
        return new MavenCoordinates(parts[0], parts[1], parts[2]);
    }

    public String getDownloadsDir() {
        return downloadsDir;
    }

    public MavenCoordinates[] getLibraries() {
        return libraries;
    }

    public boolean isForce() {
        return force;
    }

    public String getOutputFile() {
        return outputFile;
    }

    public boolean isStandardOutput() {
        return outputFile == null;
    }

    public MavenCoordinates[] getDependencies() {
        return dependencies;
    }

    public boolean isStrict() {
        return strict;
    }

    public boolean isVerifyChecksum() {
        return verifyChecksum;
    }

    /**
     * When true, parameter names are emitted as {@code arg0}, {@code arg1}, ... regardless
     * of what's in the classfile's {@code MethodParameters} or {@code LocalVariableTable}
     * attributes. The {@code hash} action sets this so the output is stable across jar
     * builds whose only difference is the presence/absence of those debug attributes.
     */
    public boolean isStableParameterNames() {
        return stableParameterNames;
    }
}
