package ru.xataaa.torrentbot.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

@Configuration
@ConditionalOnProperty(prefix = "shortcut-api", name = "enabled", havingValue = "true")
public class IphoneClientWebConfiguration implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/iphone/**")
                .addResourceLocations("classpath:/static/iphone/");
    }
}
