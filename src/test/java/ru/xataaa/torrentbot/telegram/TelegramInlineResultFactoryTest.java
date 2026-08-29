package ru.xataaa.torrentbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.xataaa.torrentbot.movie.MovieMediaType;
import ru.xataaa.torrentbot.movie.MovieMetadata;

class TelegramInlineResultFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TelegramInlineResultFactory factory = new TelegramInlineResultFactory(objectMapper);

    @Test
    void shouldCreateInlineMovieResultWithPosterAndCallback() throws Exception {
        MovieMetadata movie = new MovieMetadata(
                "abc123",
                "603",
                MovieMediaType.MOVIE,
                "Матрица",
                "The Matrix",
                1999,
                8.2,
                "Описание фильма",
                "https://image.tmdb.org/t/p/w185/poster.jpg"
        );

        String json = factory.movieResults(List.of(movie));

        JsonNode root = objectMapper.readTree(json);
        assertThat(root).hasSize(1);
        assertThat(root.get(0).get("type").asText()).isEqualTo("article");
        assertThat(root.get(0).get("title").asText()).contains("Матрица", "1999");
        assertThat(root.get(0).get("thumbnail_url").asText()).isEqualTo("https://image.tmdb.org/t/p/w185/poster.jpg");
        assertThat(root.get(0).get("reply_markup").get("inline_keyboard").get(0).get(0).get("callback_data").asText())
                .isEqualTo("movie:open:abc123");
    }

    @Test
    void shouldCreateMovieCandidatesKeyboard() {
        MovieMetadata movie = new MovieMetadata(
                "abc123",
                "603",
                MovieMediaType.TV,
                "Футурама",
                "Futurama",
                1999,
                8.5,
                "",
                ""
        );

        String keyboard = factory.movieCandidatesKeyboard(List.of(movie));

        assertThat(keyboard).contains("movie:open:abc123");
        assertThat(keyboard).contains("Футурама");
        assertThat(keyboard).contains("сериал");
    }
}
