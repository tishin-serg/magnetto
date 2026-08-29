package ru.xataaa.torrentbot.media;

import java.nio.file.Path;
import ru.xataaa.torrentbot.config.MediaLibraryProperties;

public record MediaLibraryResult(
        Path libraryPath,
        String fileName,
        MediaLibraryProperties.MoveStrategy usedStrategy,
        boolean alreadyExists
) {
}
