package io.github.paulirwin.javaapiextractor;

import java.io.File;

public record MavenCoordinates(String groupId, String artifactId, String version)
        implements Comparable<MavenCoordinates> {
    public String getJarName() {
        return "%s-%s.jar".formatted(artifactId, version);
    }

    public File getFullJarPath(ExtractContext context) {
        return new File(context.getDownloadsDir(), getJarName());
    }

    @Override
    public int compareTo(MavenCoordinates other) {
        int result = this.groupId.compareTo(other.groupId);
        if (result == 0) {
            result = this.artifactId.compareTo(other.artifactId);
            if (result == 0) {
                result = this.version.compareTo(other.version);
            }
        }
        return result;
    }
}
