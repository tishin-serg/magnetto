package ru.xataaa.torrentbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class TorrentBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TorrentBotApplication.class, args);
    }
}
