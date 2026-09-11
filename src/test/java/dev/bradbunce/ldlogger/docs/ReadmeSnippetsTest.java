package dev.bradbunce.ldlogger.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

/**
 * Ties README.md to {@link ReadmeExamples}, so a documented example cannot drift
 * away from one the compiler checks.
 *
 * <p>{@code ReadmeExamples} guarantees the snippets compile. This guarantees the
 * README still contains the snippets that were compiled. Neither alone is
 * enough: without this, the README could be edited freely and the compiled copy
 * would happily keep passing.
 */
class ReadmeSnippetsTest {

    private static final Path README = Path.of("README.md");
    private static final Path EXAMPLES =
            Path.of("src/test/java/dev/bradbunce/ldlogger/docs/ReadmeExamples.java");

    /** A fenced code block from the README. */
    private record Snippet(int line, String language, List<String> body) {

        /** Lines that must appear verbatim in the examples file, imports aside. */
        List<String> codeLines() {
            return body.stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty())
                    .filter(line -> !line.startsWith("import "))
                    .toList();
        }

        List<String> importLines() {
            return body.stream().map(String::strip).filter(l -> l.startsWith("import ")).toList();
        }
    }

    private static List<Snippet> fencedBlocks(String markdown, String language) {
        List<Snippet> blocks = new ArrayList<>();
        boolean inside = false;
        int lineNumber = 0;
        for (String raw : markdown.lines().toList()) {
            lineNumber++;
            String trimmed = raw.stripLeading();
            if (trimmed.startsWith("```")) {
                if (inside) {
                    inside = false;
                } else {
                    inside = true;
                    String fenceLanguage = trimmed.substring(3).strip();
                    blocks.add(new Snippet(lineNumber, fenceLanguage, new ArrayList<>()));
                }
            } else if (inside) {
                blocks.getLast().body().add(raw);
            }
        }
        return blocks.stream().filter(b -> b.language().equals(language)).toList();
    }

    /** Strips indentation and blank lines, so only the code itself is compared. */
    private static String canonical(List<String> lines) {
        return String.join("\n", lines);
    }

    @Test
    @DisplayName("the README has Java snippets to check")
    void hasSnippets() throws IOException {
        List<Snippet> snippets = fencedBlocks(Files.readString(README), "java");

        // Guards against the extraction silently matching nothing, which would
        // make every other assertion here vacuously true.
        assertThat(snippets).hasSizeGreaterThanOrEqualTo(10);
    }

    @TestFactory
    @DisplayName("every Java snippet in the README is compiled in ReadmeExamples")
    List<DynamicTest> everySnippetIsCompiled() throws IOException {
        String examples = Files.readString(EXAMPLES);
        String canonicalExamples = canonical(
                examples.lines().map(String::strip).filter(l -> !l.isEmpty()).toList());

        return fencedBlocks(Files.readString(README), "java").stream()
                .map(snippet -> DynamicTest.dynamicTest(
                        "README line " + snippet.line(),
                        () -> {
                            String expected = canonical(snippet.codeLines());
                            if (!canonicalExamples.contains(expected)) {
                                fail("""
                                        The Java snippet at README.md line %d is not present in
                                        %s, so it is not compile-checked. Copy it there verbatim
                                        (or update the copy) - documentation that does not compile
                                        is worse than none.

                                        Snippet:
                                        %s"""
                                        .formatted(snippet.line(), EXAMPLES, expected));
                            }
                            assertThat(snippet.importLines())
                                    .allSatisfy(anImport -> assertThat(examples).contains(anImport));
                        }))
                .toList();
    }

    @Test
    @DisplayName("the examples file only uses the library's public API")
    void examplesUseOnlyPublicApi() throws IOException {
        // It lives in a different package, so anything package-private would not
        // compile. This asserts the arrangement stays that way.
        assertThat(Files.readString(EXAMPLES))
                .contains("package dev.bradbunce.ldlogger.docs;")
                .doesNotContain("package dev.bradbunce.ldlogger;");
    }
}
