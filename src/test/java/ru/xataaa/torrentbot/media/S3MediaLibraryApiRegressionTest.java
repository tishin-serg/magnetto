package ru.xataaa.torrentbot.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.config.S3MediaProperties;
import ru.xataaa.torrentbot.regression.ApiRegression;

@ApiRegression
class S3MediaLibraryApiRegressionTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldListNewestFirstInsideConfiguredPrefix() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        server = server(exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, listResponse(
                    object("media-library/old.mkv", 10, "2026-01-01T00:00:00Z")
                            + object("media-library/new.mkv", 20, "2026-02-01T00:00:00Z")
            ));
        });

        var files = service().listFiles();

        assertThat(files).extracting(S3MediaLibraryFile::fileName).containsExactly("new.mkv", "old.mkv");
        assertThat(query.get()).contains("list-type=2", "prefix=media-library%2F");
    }

    @Test
    void shouldCleanupOnlyObjectsReturnedForConfiguredPrefix() throws Exception {
        AtomicReference<String> listQuery = new AtomicReference<>();
        AtomicReference<String> deleteBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                listQuery.set(exchange.getRequestURI().getRawQuery());
                respond(exchange, listResponse(object("media-library/movie.mkv", 42, "2026-01-01T00:00:00Z")));
                return;
            }
            deleteBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, """
                    <DeleteResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                      <Deleted><Key>media-library/movie.mkv</Key></Deleted>
                    </DeleteResult>
                    """);
        });
        server.start();

        MediaLibraryCleanupResult result = service().cleanupAllFiles();

        assertThat(result.deletedFiles()).isEqualTo(1);
        assertThat(result.deletedBytes()).isEqualTo(42);
        assertThat(listQuery.get()).contains("prefix=media-library%2F");
        assertThat(deleteBody.get()).contains("<Key>media-library/movie.mkv</Key>");
        assertThat(deleteBody.get()).doesNotContain("../", "<Key>movie.mkv</Key>");
    }

    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        created.createContext("/", handler);
        created.start();
        return created;
    }

    private S3MediaLibraryService service() {
        return new S3MediaLibraryService(new S3MediaProperties(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "us-east-1",
                "test-access",
                "test-secret",
                "bucket",
                "media-library/",
                24,
                true,
                true
        ));
    }

    private String listResponse(String objects) {
        return """
                <ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/">
                  <Name>bucket</Name>
                  <Prefix>media-library/</Prefix>
                  <KeyCount>2</KeyCount>
                  <MaxKeys>1000</MaxKeys>
                  <IsTruncated>false</IsTruncated>
                """ + objects + "</ListBucketResult>";
    }

    private String object(String key, long size, String lastModified) {
        return "<Contents><Key>" + key + "</Key><LastModified>" + lastModified
                + "</LastModified><ETag>&quot;etag&quot;</ETag><Size>" + size
                + "</Size><StorageClass>STANDARD</StorageClass></Contents>";
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String xml) throws java.io.IOException {
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/xml");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
