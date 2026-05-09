package io.github.paulirwin.javaapiextractor;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Parses Javadoc out of a {@code -sources.jar}'s {@code .java} files and indexes it
 * by qualified key so {@link RevapiReflector} can overlay it onto the metadata produced
 * from the binary jar. Revapi only feeds the binary jar to javac, which doesn't carry
 * Javadoc, so this is the only way to recover documentation from a published artifact.
 */
public class JavadocSourceParser {

    /**
     * Holds the structured Javadoc for a single declaration.
     * <ul>
     *   <li>{@code description} — leading prose before any {@code @tag}.</li>
     *   <li>{@code tags} — tag names to content (multiple uses joined with {@code " | "}).</li>
     *   <li>{@code paramDocs} — {@code @param} content positionally indexed by
     *       declaration order. Indexed by position rather than name because the binary
     *       jar may not preserve source parameter names (no {@code MethodParameters}
     *       attribute), in which case the element model surfaces them as
     *       {@code arg0}/{@code arg1}/…; positional lookup sidesteps that.</li>
     *   <li>{@code rawText} — original comment with {@code /** ... *}{@code /} markers.</li>
     * </ul>
     */
    public record JavadocEntry(String description,
                               Map<String, String> tags,
                               List<String> paramDocs,
                               String rawText) {
        public JavadocEntry {
            tags = tags == null ? Map.of() : Map.copyOf(tags);
            paramDocs = paramDocs == null ? List.of() : List.copyOf(paramDocs);
        }
    }

    private final Map<String, JavadocEntry> javadocByKey;

    private JavadocSourceParser(Map<String, JavadocEntry> javadocByKey) {
        this.javadocByKey = javadocByKey;
    }

    /**
     * Parses every {@code .java} entry in {@code sourcesJar} and returns a parser keyed
     * by qualified declaration name. Returns an empty parser (so callers can call
     * {@link #lookup} without null-checking) if the file is unreadable or the jar is
     * empty — Javadoc is best-effort, never load-bearing.
     */
    public static JavadocSourceParser fromSourcesJar(File sourcesJar) {
        if (sourcesJar == null || !sourcesJar.exists()) {
            return new JavadocSourceParser(Map.of());
        }
        var index = new HashMap<String, JavadocEntry>();
        try (var zip = new ZipFile(sourcesJar)) {
            var entries = zip.entries();
            var parser = new JavaParser();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".java")) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    ParseResult<CompilationUnit> result = parser.parse(in);
                    result.getResult().ifPresent(cu -> indexCompilationUnit(cu, index));
                } catch (IOException e) {
                    System.err.printf("Failed to read %s from %s: %s%n",
                            entry.getName(), sourcesJar.getName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.printf("Failed to open sources jar %s: %s%n",
                    sourcesJar.getAbsolutePath(), e.getMessage());
        }
        return new JavadocSourceParser(index);
    }

    public JavadocEntry lookup(String key) {
        return javadocByKey.get(key);
    }

    public boolean isEmpty() {
        return javadocByKey.isEmpty();
    }

    // ---- key constructors (used by both indexing and lookup) ----

    static String typeKey(String fqn) {
        return "T:" + fqn;
    }

    static String fieldKey(String typeFqn, String fieldName) {
        return "F:" + typeFqn + "#" + fieldName;
    }

    static String enumConstantKey(String typeFqn, String constantName) {
        return "E:" + typeFqn + "#" + constantName;
    }

    /**
     * Methods are keyed by name + arity + parameter simple type names, joined with
     * {@code ,}. We match against simple names rather than fully-qualified names because
     * source files give us short names without imports resolved — and the binary metadata
     * always uses fully-qualified names. Reducing both sides to the simple suffix lets us
     * match without a full import resolver. Ambiguous overloads (same simple-name signature)
     * fall back to first-match-wins.
     */
    static String methodKey(String typeFqn, String methodName, List<String> paramSimpleTypes) {
        return "M:" + typeFqn + "#" + methodName + "(" + String.join(",", paramSimpleTypes) + ")";
    }

    static String constructorKey(String typeFqn, List<String> paramSimpleTypes) {
        return "C:" + typeFqn + "#<init>(" + String.join(",", paramSimpleTypes) + ")";
    }

    /**
     * Reduces a type string to its rightmost segment so source-side and binary-side keys
     * agree. {@code java.util.List<java.lang.String>} → {@code List}; {@code int[]} →
     * {@code int[]}; {@code Outer$Inner} → {@code Inner}.
     */
    static String simpleTypeName(String typeName) {
        if (typeName == null || typeName.isEmpty()) {
            return "";
        }
        // Strip generics first — `<` only appears in source-side names (binary uses raw).
        int genericIdx = typeName.indexOf('<');
        String stripped = genericIdx >= 0 ? typeName.substring(0, genericIdx) : typeName;
        // Preserve the trailing array brackets so `String[]` stays distinct from `String`.
        int arrayIdx = stripped.indexOf('[');
        String head = arrayIdx >= 0 ? stripped.substring(0, arrayIdx) : stripped;
        String suffix = arrayIdx >= 0 ? stripped.substring(arrayIdx) : "";
        // Take the last `.` or `$` segment — handles both source `Outer.Inner` and binary `Outer$Inner`.
        int dot = head.lastIndexOf('.');
        int dollar = head.lastIndexOf('$');
        int cut = Math.max(dot, dollar);
        String simple = cut >= 0 ? head.substring(cut + 1) : head;
        return simple + suffix;
    }

    // ---- indexing ----

    private static void indexCompilationUnit(CompilationUnit cu, Map<String, JavadocEntry> index) {
        String pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
        for (var type : cu.getTypes()) {
            indexType(type, pkg, "", index);
        }
    }

    private static void indexType(TypeDeclaration<?> type, String pkg, String enclosingFqn,
                                  Map<String, JavadocEntry> index) {
        String typeFqn = enclosingFqn.isEmpty()
                ? (pkg.isEmpty() ? type.getNameAsString() : pkg + "." + type.getNameAsString())
                : enclosingFqn + "$" + type.getNameAsString();

        addEntry(index, typeKey(typeFqn), type, List.of());

        // Members
        for (var member : type.getMembers()) {
            if (member instanceof FieldDeclaration field) {
                for (var var : field.getVariables()) {
                    addEntry(index, fieldKey(typeFqn, var.getNameAsString()), field, List.of());
                }
            } else if (member instanceof MethodDeclaration method) {
                var paramSimpleTypes = method.getParameters().stream()
                        .map(p -> simpleTypeName(p.getType().asString()) + (p.isVarArgs() ? "[]" : ""))
                        .toList();
                var paramNames = method.getParameters().stream()
                        .map(p -> p.getNameAsString())
                        .toList();
                addEntry(index, methodKey(typeFqn, method.getNameAsString(), paramSimpleTypes),
                        method, paramNames);
            } else if (member instanceof ConstructorDeclaration ctor) {
                var paramSimpleTypes = ctor.getParameters().stream()
                        .map(p -> simpleTypeName(p.getType().asString()) + (p.isVarArgs() ? "[]" : ""))
                        .toList();
                var paramNames = ctor.getParameters().stream()
                        .map(p -> p.getNameAsString())
                        .toList();
                addEntry(index, constructorKey(typeFqn, paramSimpleTypes), ctor, paramNames);
            } else if (member instanceof TypeDeclaration<?> nested) {
                // Recurse into nested types.
                indexType(nested, pkg, typeFqn, index);
            }
        }

        // Enum constants live separately from members on EnumDeclaration.
        if (type instanceof EnumDeclaration enumDecl) {
            for (EnumConstantDeclaration constant : enumDecl.getEntries()) {
                addEntry(index, enumConstantKey(typeFqn, constant.getNameAsString()),
                        constant, List.of());
            }
        }
    }

    private static <T extends NodeWithJavadoc<?>> void addEntry(
            Map<String, JavadocEntry> index, String key, T node, List<String> paramNames) {
        Optional<Comment> commentOpt = node.getComment();
        var jdOpt = node.getJavadocComment();
        if (jdOpt.isEmpty() && (commentOpt.isEmpty() || !commentOpt.get().isJavadocComment())) {
            return;
        }
        String raw = jdOpt.map(c -> "/**" + c.getContent() + "*/")
                .orElseGet(() -> commentOpt.get().toString());

        // Pull out structured fields with JavaParser's own Javadoc parser. This is much
        // more robust than regex-matching on raw text — it correctly distinguishes
        // {@link …} inline tags inside the content of an @param block from a new tag.
        String description = "";
        var tags = new HashMap<String, String>();
        var paramDocsByName = new HashMap<String, String>();
        try {
            var jd = node.getJavadoc();
            if (jd.isPresent()) {
                description = trimDescription(jd.get().getDescription().toText());
                for (var tag : jd.get().getBlockTags()) {
                    String tagName = tag.getTagName();
                    String content = tag.getContent().toText().trim();
                    if (tag.getType() == com.github.javaparser.javadoc.JavadocBlockTag.Type.PARAM) {
                        tag.getName().ifPresent(n -> {
                            if (!content.isEmpty()) {
                                paramDocsByName.put(n, contentAfterParamName(content, n));
                            }
                        });
                    }
                    // Even @param tags go into the tags map so consumers see the union of
                    // parameter docs without having to read paramDocs separately. Multiple
                    // @throws (or other repeating tags) are joined with " | ".
                    String formatted = tag.getName()
                            .map(n -> n + " " + contentAfterParamName(content, n))
                            .orElse(content);
                    tags.merge(tagName, formatted, (a, b) -> a + " | " + b);
                }
            }
        } catch (RuntimeException e) {
            // JavaParser's Javadoc tag parser can throw on malformed comments; degrade
            // to "description only" rather than dropping the entry entirely.
        }

        // Build positional paramDocs in declaration order. Empty string at index i means
        // "no @param documented for this parameter" (vs. null which means the entry has
        // no parameters at all). Some authors omit @param tags for self-explanatory
        // parameters; we keep the slot so callers can still index by position.
        List<String> paramDocs;
        if (paramNames.isEmpty()) {
            paramDocs = List.of();
        } else {
            var positional = new ArrayList<String>(paramNames.size());
            for (var name : paramNames) {
                String doc = paramDocsByName.get(name);
                positional.add(doc == null ? "" : doc);
            }
            paramDocs = positional;
        }

        // First-match-wins for overload-ambiguous keys.
        index.putIfAbsent(key, new JavadocEntry(
                description.isBlank() ? null : description,
                tags.isEmpty() ? null : tags,
                paramDocs,
                raw));
    }

    /**
     * Strips trailing whitespace and stray asterisks from a description. JavaParser's
     * {@code getDescription().toText()} preserves any literal {@code *} that appears
     * before the closing {@code *}{@code /} in non-canonical comments like
     * {@code /** ... *}{@code *}{@code /} (Lucene does this).
     */
    private static String trimDescription(String text) {
        String trimmed = text.trim();
        // Repeatedly strip trailing whitespace+asterisk pairs.
        while (trimmed.endsWith("*")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * For a {@code @param name description…} tag JavaParser's {@code getContent()} is just
     * "description…", but it's surfaced via the tag's {@code getName()} instead. Other
     * named tags (e.g. {@code @throws Type description}) include the type as the first
     * token of {@code content}. Both cases need different handling at the call site —
     * this helper normalizes "content with the name stripped if duplicated".
     */
    private static String contentAfterParamName(String content, String name) {
        if (content.startsWith(name + " ")) {
            return content.substring(name.length() + 1).trim();
        }
        return content;
    }

    /** Test-visibility hook for unit tests. */
    static List<String> indexedKeys(JavadocSourceParser parser) {
        return new ArrayList<>(parser.javadocByKey.keySet());
    }
}
