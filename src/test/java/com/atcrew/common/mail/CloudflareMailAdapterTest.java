package com.atcrew.common.mail;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cloudflare Email Sending REST 호출 형식과 실패 판정을 로컬 HTTP 서버로 고정한다. */
class CloudflareMailAdapterTest {

    private static final MailProperties PROPS = new MailProperties("no-reply@mail.at-crew.com", "AT-CREW",
            new MailProperties.Cloudflare("acc-1", "token-1"));

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private SimpleMeterRegistry registry;
    private CloudflareMailAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        registry = new SimpleMeterRegistry();
        adapter = new CloudflareMailAdapter(PROPS, registry,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/client/v4");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void 계정_경로로_토큰과_발신자_객체를_담아_보낸다() {
        respond(200, "{\"success\":true,\"errors\":[],\"result\":{\"delivered\":[\"a@b.com\"],\"permanent_bounces\":[],\"queued\":[]}}");

        adapter.send("a@b.com", "제목", "<p>본문</p>");

        assertThat(path.get()).isEqualTo("/client/v4/accounts/acc-1/email/sending/send");
        assertThat(auth.get()).isEqualTo("Bearer token-1");
        assertThat(body.get())
                .contains("\"to\":\"a@b.com\"")
                .contains("\"subject\":\"제목\"")
                .contains("\"address\":\"no-reply@mail.at-crew.com\"")
                .contains("\"name\":\"AT-CREW\"");
        assertThat(failures()).isZero();
    }

    @Test
    void 대기열_접수도_성공으로_본다() {
        respond(200, "{\"success\":true,\"errors\":[],\"result\":{\"delivered\":[],\"permanent_bounces\":[],\"queued\":[\"a@b.com\"]}}");

        adapter.send("a@b.com", "제목", "<p>본문</p>");

        assertThat(failures()).isZero();
    }

    @Test
    void 영구_반송이면_실패로_센다() {
        respond(200, "{\"success\":true,\"errors\":[],\"result\":{\"delivered\":[],\"permanent_bounces\":[\"a@b.com\"],\"queued\":[]}}");

        adapter.send("a@b.com", "제목", "<p>본문</p>");

        assertThat(failures()).isEqualTo(1.0);
    }

    @Test
    void 인증_실패는_예외를_올리지_않고_실패로_센다() {
        respond(401, "{\"success\":false,\"errors\":[{\"code\":10000,\"message\":\"Authentication error\"}],\"result\":null}");

        adapter.send("a@b.com", "제목", "<p>본문</p>");

        assertThat(failures()).isEqualTo(1.0);
    }

    @Test
    void 설정값이_비었거나_환경변수가_풀리지_않았으면_기동을_멈춘다() {
        assertThatThrownBy(() -> new MailProperties("no-reply@mail.at-crew.com", "AT-CREW",
                new MailProperties.Cloudflare("acc-1", "${CLOUDFLARE_EMAIL_API_TOKEN}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mail.cloudflare.api-token");
        assertThatThrownBy(() -> new MailProperties(" ", "AT-CREW", new MailProperties.Cloudflare("acc-1", "t")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mail.from-address");
        assertThatThrownBy(() -> new MailProperties("no-reply@mail.at-crew.com", "AT-CREW", null))
                .isInstanceOf(IllegalStateException.class);
    }

    private void respond(int status, String json) {
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
    }

    private double failures() {
        return registry.get("atcrew.mail.send.failure").counter().count();
    }
}
