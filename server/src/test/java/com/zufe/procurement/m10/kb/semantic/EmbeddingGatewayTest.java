package com.zufe.procurement.m10.kb.semantic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class EmbeddingGatewayTest {
    @Test void reorderedBatchIsMappedByIndexAndBatchLimitIsEnforced() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/embeddings", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"usage\":{\"total_tokens\":7},\"data\":[{\"index\":1,\"embedding\":[0,1]},{\"index\":0,\"embedding\":[1,0]}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            EmbeddingProperties p = properties(server);
            EmbeddingGateway gateway = new EmbeddingGateway(p, new ObjectMapper());
            var result = gateway.embedWithUsage(List.of("first", "second"), false);
            assertThat(result.vectors().get(0)).containsExactly(1, 0);
            assertThat(result.totalTokens()).isEqualTo(7L);
            assertThatThrownBy(() -> gateway.embed(java.util.Collections.nCopies(11, "text"), false))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }
    @Test void rateLimitIsNotRetried() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/embeddings", exchange -> { calls.incrementAndGet(); exchange.sendResponseHeaders(429,-1); exchange.close(); });
        server.start();
        try {
            assertThatThrownBy(() -> new EmbeddingGateway(properties(server), new ObjectMapper()).embed(List.of("q"), true))
                    .hasMessage("embedding_http_429");
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }
    private EmbeddingProperties properties(HttpServer server) {
        EmbeddingProperties p = new EmbeddingProperties(); p.setApiKey("simulated-key"); p.setDimensions(2);
        p.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"); return p;
    }
    @ParameterizedTest @ValueSource(strings = {"not json", "{\"data\":[{\"index\":0,\"embedding\":[1]}]}",
            "{\"data\":[{\"index\":0,\"embedding\":[0,0]}]}"})
    void invalidProviderResponsesNeverProduceVectors(String payload) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> {
            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            assertThatThrownBy(() -> new EmbeddingGateway(properties(server), new ObjectMapper()).embed(List.of("q"), true))
                    .isInstanceOf(IllegalStateException.class);
        } finally { server.stop(0); }
    }
    @Test void queryTimeoutIsBoundedAndDoesNotRetry() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/v1/embeddings", exchange -> {
            calls.incrementAndGet();
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            EmbeddingProperties p = properties(server); p.setQueryTimeoutMs(100);
            EmbeddingGateway gateway = new EmbeddingGateway(p, new ObjectMapper());
            long started = System.nanoTime();
            assertThatThrownBy(() -> gateway.embed(List.of("q"), true)).hasMessage("embedding_timeout");
            assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(1500);
            assertThat(calls.get()).isLessThanOrEqualTo(1);
        } finally { server.stop(0); }
    }
}
