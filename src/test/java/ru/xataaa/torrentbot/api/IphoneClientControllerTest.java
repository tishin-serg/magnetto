package ru.xataaa.torrentbot.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

class IphoneClientControllerTest {

    @Test
    void mapsBothClientUrlsToStaticApplication() throws Exception {
        GetMapping mapping = IphoneClientController.class
                .getMethod("iphoneClient")
                .getAnnotation(GetMapping.class);

        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/iphone", "/iphone/", "/api/iphone", "/api/iphone/"}, mapping.value());
        ResponseEntity<Void> response = new IphoneClientController().iphoneClient();
        assertEquals(302, response.getStatusCode().value());
        assertEquals(URI.create("/api/iphone/index.html"), response.getHeaders().getLocation());
    }

    @Test
    void packagesRequiredClientResources() {
        ClassLoader loader = getClass().getClassLoader();

        assertNotNull(loader.getResource("static/iphone/index.html"));
        assertNotNull(loader.getResource("static/iphone/app.js"));
        assertNotNull(loader.getResource("static/iphone/styles.css"));
        assertNotNull(loader.getResource("static/iphone/manifest.webmanifest"));
        assertNotNull(loader.getResource("static/iphone/icon.svg"));
    }
}
