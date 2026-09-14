package ru.xataaa.torrentbot.api;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(prefix = "shortcut-api", name = "enabled", havingValue = "true")
public class IphoneClientController {

    @GetMapping({"/iphone", "/iphone/"})
    public ResponseEntity<Void> iphoneClient() {
        return ResponseEntity.status(302)
                .location(URI.create("/iphone/index.html"))
                .build();
    }
}
