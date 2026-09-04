package ru.xataaa.torrentbot.config;

import io.netty.channel.ChannelOption;
import io.netty.resolver.ResolvedAddressTypes;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    public static ReactorClientHttpConnector connector(int connectTimeoutMs, int requestTimeoutMs) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .resolver(resolver -> resolver
                        .queryTimeout(Duration.ofMillis(connectTimeoutMs))
                        .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY))
                .responseTimeout(Duration.ofMillis(requestTimeoutMs));
        return new ReactorClientHttpConnector(httpClient);
    }
}
