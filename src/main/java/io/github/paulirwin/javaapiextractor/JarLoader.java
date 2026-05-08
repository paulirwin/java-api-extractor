package io.github.paulirwin.javaapiextractor;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.stream.Stream;

public class JarLoader {
    public static URLClassLoader loadJars(ExtractContext context) {
        var jarUrls = Stream.concat(
                        Stream.of(context.getLibraries()),
                        Stream.of(context.getDependencies()))
                .map(library -> {
                    try {
                        return library.getFullJarPath(context).toURI().toURL();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toArray(URL[]::new);

        return new URLClassLoader(jarUrls, JarLoader.class.getClassLoader());
    }
}

