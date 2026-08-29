package ru.xataaa.torrentbot.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.xataaa.torrentbot.config.MediaLibraryProperties;

class MediaLibraryServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldAddFileToLibrary() throws Exception {
        Path sourceFile = tempDirectory.resolve("source.mkv");
        Files.writeString(sourceFile, "movie");
        MediaLibraryService service = service(MediaLibraryProperties.MoveStrategy.HARDLINK, MediaLibraryProperties.MoveStrategy.COPY);

        MediaLibraryResult result = service.addToLibrary(sourceFile, "Movie.mkv");

        assertThat(Files.exists(result.libraryPath())).isTrue();
        assertThat(result.fileName()).isEqualTo("Movie.mkv");
    }

    @Test
    void shouldNormalizeFileNameAndPreventTraversal() throws Exception {
        Path sourceFile = tempDirectory.resolve("source.mkv");
        Files.writeString(sourceFile, "movie");
        MediaLibraryService service = service(MediaLibraryProperties.MoveStrategy.COPY, MediaLibraryProperties.MoveStrategy.COPY);

        MediaLibraryResult result = service.addToLibrary(sourceFile, "../bad/name.mkv");

        assertThat(result.libraryPath()).startsWith(tempDirectory.resolve("media").toAbsolutePath().normalize());
        assertThat(result.fileName()).doesNotContain("..");
        assertThat(result.fileName()).doesNotContain("/");
    }

    @Test
    void shouldFallbackToCopyWhenHardlinkFails() throws Exception {
        Path sourceFile = tempDirectory.resolve("source.mkv");
        Files.writeString(sourceFile, "movie");
        MediaLibraryService service = service(MediaLibraryProperties.MoveStrategy.MOVE, MediaLibraryProperties.MoveStrategy.COPY);
        Files.createDirectories(tempDirectory.resolve("media"));
        Files.writeString(tempDirectory.resolve("media").resolve("Movie.mkv"), "different");

        MediaLibraryResult result = service.addToLibrary(sourceFile, "Movie.mkv");

        assertThat(Files.exists(result.libraryPath())).isTrue();
        assertThat(result.fileName()).startsWith("Movie-");
    }

    @Test
    void shouldNotOverwriteExistingFile() throws Exception {
        Path sourceFile = tempDirectory.resolve("source.mkv");
        Files.writeString(sourceFile, "movie");
        MediaLibraryService service = service(MediaLibraryProperties.MoveStrategy.COPY, MediaLibraryProperties.MoveStrategy.COPY);

        MediaLibraryResult firstResult = service.addToLibrary(sourceFile, "Movie.mkv");
        MediaLibraryResult secondResult = service.addToLibrary(sourceFile, "Movie.mkv");

        assertThat(firstResult.libraryPath()).isNotEqualTo(secondResult.libraryPath());
    }

    @Test
    void shouldCleanupOnlyMediaLibraryFiles() throws Exception {
        MediaLibraryService service = service(MediaLibraryProperties.MoveStrategy.COPY, MediaLibraryProperties.MoveStrategy.COPY);
        Path mediaDirectory = tempDirectory.resolve("media");
        Path nestedDirectory = mediaDirectory.resolve("nested");
        Files.createDirectories(nestedDirectory);
        Files.writeString(mediaDirectory.resolve("Movie.mkv"), "movie");
        Files.writeString(nestedDirectory.resolve("Episode.mkv"), "episode");
        Path outsideFile = tempDirectory.resolve("outside.mkv");
        Files.writeString(outsideFile, "outside");

        MediaLibraryCleanupResult result = service.cleanupAllFiles();

        assertThat(result.deletedFiles()).isEqualTo(2);
        assertThat(result.deletedBytes()).isGreaterThan(0);
        assertThat(Files.exists(outsideFile)).isTrue();
        assertThat(Files.exists(mediaDirectory)).isTrue();
        assertThat(Files.list(mediaDirectory)).isEmpty();
    }

    private MediaLibraryService service(
            MediaLibraryProperties.MoveStrategy moveStrategy,
            MediaLibraryProperties.MoveStrategy fallbackMoveStrategy
    ) {
        return new MediaLibraryService(new MediaLibraryProperties(
                true,
                tempDirectory.resolve("media").toString(),
                "https://example.com/dav/",
                moveStrategy,
                fallbackMoveStrategy,
                false,
                30
        ));
    }
}
