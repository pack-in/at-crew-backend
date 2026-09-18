package com.atcrew;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 커밋된 다이어그램이 코드와 어긋나지 않았는지 검사한다.
 *
 * <p>다이어그램은 scripts/diagrams/build.py로 사람이 다시 만든다. 이 테스트는 다시 만드는 걸 잊었거나
 * 코드가 바뀌어 그림이 틀려진 상태를 빌드에서 잡는다.
 * <ul>
 *   <li>modules.mmd의 화살표가 Spring Modulith가 계산한 모듈 의존과 같은지 — 완전한 대조</li>
 *   <li>archify SVG가 지금의 IR·archify 버전으로 만들어졌는지 — build.py가 새긴 해시와 비교</li>
 *   <li>IR에 적힌 코드 식별자(앵커)가 아직 코드에 있는지 — 주석은 빼고 찾는다. 이름 변경·삭제만 잡고
 *       흐름에 단계가 추가된 것은 잡지 못한다</li>
 * </ul>
 */
class DiagramConsistencyTests {

    private static final Path ASSETS = Path.of("docs", "assets");
    private static final Path ARCHIFY_LOCK = Path.of("scripts", "diagrams", "archify.lock.json");
    private static final String COMMON = "common";
    /** scripts/diagrams/build.py의 MERMAID_SOURCE_TAG와 같은 값. */
    private static final String MERMAID_SOURCE_TAG = "mermaid";
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(^|\\s)//.*$", Pattern.MULTILINE);
    private static final Pattern HASH_COMMENT_LINE = Pattern.compile("^\\s*#.*$", Pattern.MULTILINE);

    private static final Pattern IR_FILE =
            Pattern.compile("(.+)\\.(architecture|workflow|sequence|dataflow|lifecycle)\\.json");
    private static final Pattern SOURCE_SHA256 =
            Pattern.compile("<svg\\b[^>]*\\bdata-source-sha256=\"([0-9a-f]{64})\"");
    private static final Pattern MMD_EDGE =
            Pattern.compile("^\\s*([A-Za-z][\\w-]*)\\s*(?:-->|-\\.->)\\s*([A-Za-z][\\w-]*)\\s*$");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void archify_다이어그램이_지금의_IR과_버전으로_생성돼_있다() throws IOException {
        String version = JSON.readTree(ARCHIFY_LOCK.toFile()).get("version").asString();
        List<String> problems = new ArrayList<>();

        for (Path ir : archifySources()) {
            String name = diagramName(ir);
            Path svg = ASSETS.resolve(name + ".svg");
            if (!Files.exists(svg)) {
                problems.add(svg + " 이 없다");
                continue;
            }
            Matcher m = SOURCE_SHA256.matcher(Files.readString(svg));
            String expected = sourceSha256(version, ir);
            if (!m.find()) {
                problems.add(svg + " 에 data-source-sha256이 없다");
            } else if (!m.group(1).equals(expected)) {
                problems.add(svg + " 이 지금의 IR·archify " + version + "로 만든 것이 아니다 (SVG "
                        + m.group(1) + ", 기대 " + expected + ")");
            }
        }

        assertThat(problems)
                .as("다이어그램을 다시 만든다: python3 scripts/diagrams/build.py <이름>\n%s", String.join("\n", problems))
                .isEmpty();
    }

    // 앵커가 주석에만 남아 있으면 코드에서 사라진 요소를 그림이 계속 보여 준다 — 주석은 빼고 찾는다.
    @Test
    void 앵커는_주석을_뺀_본문에서_찾는다() {
        String java = "class A {\n  // TrashPurgeScheduler가 지운다\n  /* moveToTrash 참고 */\n  String url = \"https://x\";\n}";
        assertThat(withoutComments(Path.of("A.java"), java))
                .doesNotContain("TrashPurgeScheduler").doesNotContain("moveToTrash").contains("https://x");
        String yaml = "      # nginx -t로 검증한다\n      - name: 헬스체크\n        run: curl liveness\n";
        assertThat(withoutComments(Path.of("deploy.yml"), yaml))
                .doesNotContain("nginx -t").contains("헬스체크").contains("liveness");
    }

    @Test
    void 모듈_다이어그램_SVG가_지금의_mmd로_생성돼_있다() throws IOException {
        Path svg = ASSETS.resolve("modules.svg");
        Matcher m = SOURCE_SHA256.matcher(Files.readString(svg));
        String expected = sourceSha256(MERMAID_SOURCE_TAG, ASSETS.resolve("modules.mmd"));
        assertThat(m.find() ? m.group(1) : "(없음)")
                .as("modules.mmd를 고쳤으면 python3 scripts/diagrams/build.py modules로 SVG를 다시 만든다")
                .isEqualTo(expected);
    }

    @Test
    void archify_다이어그램의_코드_앵커가_아직_코드에_있다() throws IOException {
        List<String> problems = new ArrayList<>();

        for (Path ir : archifySources()) {
            Path anchorsFile = ASSETS.resolve(diagramName(ir) + ".anchors.json");
            if (!Files.exists(anchorsFile)) {
                problems.add(anchorsFile + " 이 없다 — IR마다 코드 앵커를 한 개 이상 둔다");
                continue;
            }
            String irText = Files.readString(ir);
            JsonNode anchors = JSON.readTree(anchorsFile.toFile()).get("anchors");
            if (anchors == null || anchors.isEmpty()) {
                problems.add(anchorsFile + " 에 앵커가 없다");
                continue;
            }
            for (JsonNode anchor : anchors) {
                String text = anchor.get("text").asString();
                Path scope = Path.of(anchor.get("path").asString());
                if (!irText.contains(text)) {
                    problems.add(anchorsFile + ": '" + text + "' 가 " + ir + " 에 없다 — 그림에 없는 것은 앵커로 두지 않는다");
                }
                if (!Files.exists(scope)) {
                    problems.add(anchorsFile + ": 경로 " + scope + " 가 없다");
                } else if (!containsText(scope, text)) {
                    problems.add(anchorsFile + ": '" + text + "' 가 " + scope + " 아래 코드에 없다 — 이름이 바뀌었으면 IR과 앵커를 함께 고친다");
                }
            }
        }

        assertThat(problems).as(String.join("\n", problems)).isEmpty();
    }

    @Test
    void 모듈_다이어그램의_화살표가_실제_모듈_의존과_같다() throws IOException {
        Set<String> drawn = new TreeSet<>();
        List<String> unparsed = new ArrayList<>();
        for (String line : Files.readAllLines(ASSETS.resolve("modules.mmd"))) {
            if (line.strip().startsWith("%%") || !(line.contains("-->") || line.contains("-.->"))) {
                continue;
            }
            Matcher m = MMD_EDGE.matcher(line);
            if (m.matches()) {
                drawn.add(m.group(1) + " -> " + m.group(2));
            } else {
                unparsed.add(line.strip());
            }
        }
        assertThat(unparsed).as("라벨 없는 'a --> b' 또는 'a -.-> b' 형식만 쓴다").isEmpty();

        ApplicationModules modules = ApplicationModules.of(AtCrewBackendApplication.class);
        Set<String> actual = new TreeSet<>();
        for (ApplicationModule module : modules) {
            String source = idOf(module);
            if (source.equals(COMMON)) {
                continue;
            }
            module.getDirectDependencies(modules).uniqueModules()
                    .map(DiagramConsistencyTests::idOf)
                    .filter(target -> !target.equals(COMMON))
                    .forEach(target -> actual.add(source + " -> " + target));
        }

        Set<String> missing = new TreeSet<>(actual);
        missing.removeAll(drawn);
        Set<String> extra = new TreeSet<>(drawn);
        extra.removeAll(actual);
        assertThat(missing.isEmpty() && extra.isEmpty())
                .as("docs/assets/modules.mmd를 고친 뒤 python3 scripts/diagrams/build.py modules\n"
                        + "그려야 하는데 빠진 의존: %s\n실제로는 없는 의존: %s", missing, extra)
                .isTrue();
    }

    @Test
    void common을_제외한_모든_모듈이_common을_사용한다() {
        // modules.mmd는 common으로 가는 선 대신 "모든 모듈이 사용"이라고 적어 둔다. 그 문구가 사실인지 본다.
        ApplicationModules modules = ApplicationModules.of(AtCrewBackendApplication.class);
        List<String> notUsing = modules.stream()
                .filter(m -> !idOf(m).equals(COMMON))
                .filter(m -> m.getDirectDependencies(modules).uniqueModules().noneMatch(t -> idOf(t).equals(COMMON)))
                .map(DiagramConsistencyTests::idOf)
                .toList();
        assertThat(notUsing).as("modules.mmd의 common 라벨을 고친다").isEmpty();
    }

    private static List<Path> archifySources() throws IOException {
        try (Stream<Path> files = Files.list(ASSETS)) {
            List<Path> found = files.filter(p -> IR_FILE.matcher(p.getFileName().toString()).matches())
                    .sorted()
                    .toList();
            // 이름이 같으면 두 IR이 SVG 하나를 나눠 써서 한쪽 검사는 영원히 실패한다(build.py도 같은 이유로 멈춘다).
            assertThat(found.stream().map(DiagramConsistencyTests::diagramName).toList())
                    .as("archify IR 이름이 겹친다 — SVG 이름이 IR 이름에서 나온다").doesNotHaveDuplicates();
            // 작업 디렉터리가 달라 아무것도 못 찾으면 검사가 조용히 통과해 버린다.
            assertThat(found).as(ASSETS.toAbsolutePath() + " 에서 archify IR을 찾지 못했다").isNotEmpty();
            return found;
        }
    }

    private static String diagramName(Path ir) {
        Matcher m = IR_FILE.matcher(ir.getFileName().toString());
        if (!m.matches()) {
            throw new IllegalArgumentException(ir.toString());
        }
        return m.group(1);
    }

    /** scripts/diagrams/build.py의 source_sha256과 같은 계산. 한쪽을 바꾸면 다른 쪽도 바꾼다. */
    private static String sourceSha256(String version, Path ir) throws IOException {
        byte[] body = Files.readString(ir).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update((version + "\n").getBytes(StandardCharsets.UTF_8));
            sha.update(body);
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** path는 디렉터리(아래 파일 전부)나 파일 하나를 가리킨다. 흔한 문자열이면 파일로 좁혀야 의미가 있다. */
    private static boolean containsText(Path path, String text) throws IOException {
        try (Stream<Path> files = Files.walk(path)) {
            return files.filter(Files::isRegularFile).anyMatch(f -> withoutComments(f, readText(f)).contains(text));
        }
    }

    /**
     * 주석을 뺀 본문. 식별자가 주석에만 남아 있어도 앵커가 통과하면, 코드에서 사라진 요소를 그림이 계속 보여 준다.
     * 확장자로 주석 문법을 고른다 — 모르는 형식은 그대로 둔다.
     */
    static String withoutComments(Path file, String text) {
        String name = file.getFileName().toString();
        if (name.matches(".*\\.(java|kt|js|mjs|ts)$")) {
            return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(text).replaceAll("")).replaceAll("$1");
        }
        if (name.matches(".*\\.(ya?ml|sh|py|conf|toml)$")) {
            return HASH_COMMENT_LINE.matcher(text).replaceAll("");
        }
        return text;
    }

    private static String readText(Path file) {
        try {
            return Files.readString(file);
        } catch (CharacterCodingException e) {
            return "";  // 바이너리 파일은 건너뛴다
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String idOf(ApplicationModule module) {
        return module.getIdentifier().toString();
    }
}
