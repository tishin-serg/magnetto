package ru.xataaa.torrentbot.torrentsearch;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record EpisodeRange(int start, int end) {
    public EpisodeRange {
        if (start <= 0 || end <= 0) {
            throw new IllegalArgumentException("Episode numbers must be positive");
        }
        if (end < start) {
            int originalStart = start;
            start = end;
            end = originalStart;
        }
    }

    public boolean contains(int episodeNumber) {
        return episodeNumber >= start && episodeNumber <= end;
    }

    public boolean multiEpisode() {
        return start != end;
    }

    public Set<Integer> episodes() {
        return IntStream.rangeClosed(start, end)
                .boxed()
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public String label() {
        if (multiEpisode()) {
            return start + "-" + end;
        }
        return Integer.toString(start);
    }
}
