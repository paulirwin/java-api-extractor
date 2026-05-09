package io.github.paulirwin.javaapiextractor;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;

public class JarDownloader {
    // Package-private and non-final so tests can rebind it to a local HTTP fixture.
    static String MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private static volatile HttpClient httpClient;

    public static void downloadMavenDependency(ExtractContext context, MavenCoordinates dependency, boolean force) {
        var downloadDir = new File(context.getDownloadsDir());
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            throw new RuntimeException("Failed to create download directory: " + downloadDir.getAbsolutePath());
        }

        var jarName = dependency.getJarName();
        var jarFile = new File(downloadDir, jarName);
        if (jarFile.exists() && !force) {
            System.err.printf("File %s already exists. Skipping download.%n", jarName);
            return;
        }

        var jarUrl = mavenUrl(dependency, jarName);
        System.err.printf("Downloading %s%n", jarUrl);

        downloadWithRetry(jarUrl, jarFile);

        if (context.isVerifyChecksum()) {
            verifyChecksum(dependency, jarFile);
        }

        System.err.printf("Downloaded %s (%d bytes)%n", jarFile.getAbsolutePath(), jarFile.length());
    }

    /**
     * When true, {@link #downloadSourcesJar} returns {@code null} immediately without
     * touching the network. Tests with synthetic Maven coordinates use this to avoid
     * hitting real Maven Central with bogus group IDs.
     */
    static volatile boolean skipSourcesFetch = false;

    /**
     * Best-effort fetch of the {@code -sources.jar} sidecar from Maven Central. Used to
     * reconstruct Javadoc from {@code .java} files (the binary jar doesn't carry it). A
     * 404 returns {@code null} with a warning — not every artifact publishes sources.
     *
     * @return the local sources jar, or {@code null} if Maven Central doesn't have one
     *         or the {@link #skipSourcesFetch} switch is on
     */
    public static File downloadSourcesJar(ExtractContext context, MavenCoordinates dependency, boolean force) {
        if (skipSourcesFetch) {
            return null;
        }
        var downloadDir = new File(context.getDownloadsDir());
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            throw new RuntimeException("Failed to create download directory: " + downloadDir.getAbsolutePath());
        }

        var sourcesJarName = "%s-%s-sources.jar".formatted(dependency.artifactId(), dependency.version());
        var sourcesJarFile = new File(downloadDir, sourcesJarName);
        // Treat an empty stale file as "no sources jar" — earlier 404 cleanup may have
        // failed (e.g. on Windows the file handle from HttpResponse.BodyHandlers.ofFile
        // can linger), and we don't want to feed a zero-byte file to the parser.
        if (sourcesJarFile.exists() && sourcesJarFile.length() == 0) {
            sourcesJarFile.delete();
        }
        if (sourcesJarFile.exists() && !force) {
            System.err.printf("File %s already exists. Skipping download.%n", sourcesJarName);
            return sourcesJarFile;
        }

        var sourcesUrl = mavenUrl(dependency, sourcesJarName);
        System.err.printf("Downloading %s%n", sourcesUrl);

        try {
            var request = HttpRequest.newBuilder(URI.create(sourcesUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            var response = client().send(request, HttpResponse.BodyHandlers.ofFile(sourcesJarFile.toPath()));
            if (response.statusCode() == 404) {
                // No sources jar published — common for in-house artifacts. Don't fail
                // the whole extract; Javadoc just won't be available for this library.
                System.err.printf("No -sources.jar published for %s; Javadoc will be unavailable.%n",
                        dependency.getJarName());
                // ofFile created an empty file even on 404; clean it up so the next run
                // doesn't hit the "already exists" branch with a zero-byte file.
                if (sourcesJarFile.exists()) {
                    sourcesJarFile.delete();
                }
                return null;
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + sourcesUrl);
            }
        } catch (IOException e) {
            System.err.printf("Failed to fetch sources jar for %s: %s; Javadoc will be unavailable.%n",
                    dependency.getJarName(), e.getMessage());
            if (sourcesJarFile.exists() && sourcesJarFile.length() == 0) {
                sourcesJarFile.delete();
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while downloading " + sourcesUrl, e);
        }

        System.err.printf("Downloaded %s (%d bytes)%n", sourcesJarFile.getAbsolutePath(), sourcesJarFile.length());
        return sourcesJarFile;
    }

    private static String mavenUrl(MavenCoordinates coords, String fileName) {
        var groupPath = coords.groupId().replace(".", "/");
        return "%s/%s/%s/%s/%s".formatted(MAVEN_CENTRAL, groupPath, coords.artifactId(), coords.version(), fileName);
    }

    private static void downloadWithRetry(String url, File destination) {
        IOException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                var request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();
                var response = client().send(request, HttpResponse.BodyHandlers.ofFile(destination.toPath()));
                if (response.statusCode() == 200) {
                    return;
                }
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            } catch (IOException e) {
                lastFailure = e;
                if (attempt < MAX_ATTEMPTS) {
                    System.err.printf("Attempt %d/%d failed (%s); retrying…%n", attempt, MAX_ATTEMPTS, e.getMessage());
                    sleep(500L * attempt);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while downloading " + url, e);
            }
        }
        throw new RuntimeException("Failed to download " + url + " after " + MAX_ATTEMPTS + " attempts", lastFailure);
    }

    private static void verifyChecksum(MavenCoordinates coords, File jarFile) {
        var sha1Url = mavenUrl(coords, coords.getJarName() + ".sha1");
        try {
            var request = HttpRequest.newBuilder(URI.create(sha1Url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            var response = client().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // Checksum sidecar is missing; warn but don't fail — not every artifact publishes one.
                System.err.printf("No .sha1 sidecar available for %s (HTTP %d); skipping verification.%n",
                        coords.getJarName(), response.statusCode());
                return;
            }
            var expected = response.body().trim().split("\\s+")[0].toLowerCase();
            var actual = sha1Hex(jarFile);
            if (!expected.equalsIgnoreCase(actual)) {
                throw new RuntimeException("SHA-1 mismatch for " + coords.getJarName()
                        + ": expected " + expected + ", got " + actual);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to fetch checksum for " + coords.getJarName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while fetching checksum for " + coords.getJarName(), e);
        }
    }

    private static String sha1Hex(File file) throws IOException {
        try {
            var digest = MessageDigest.getInstance("SHA-1");
            try (var in = Files.newInputStream(file.toPath())) {
                var buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            var hash = digest.digest();
            var sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 not available on this JVM", e);
        }
    }

    private static HttpClient client() {
        var local = httpClient;
        if (local == null) {
            synchronized (JarDownloader.class) {
                local = httpClient;
                if (local == null) {
                    local = HttpClient.newBuilder()
                            .connectTimeout(CONNECT_TIMEOUT)
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                    httpClient = local;
                }
            }
        }
        return local;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
