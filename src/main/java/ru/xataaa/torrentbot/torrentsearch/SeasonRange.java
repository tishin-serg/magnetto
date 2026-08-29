package ru.xataaa.torrentbot.torrentsearch;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record SeasonRange(int start, int end) {
    public SeasonRange {
        if (start <= 0 || end <= 0) {
            throw new IllegalArgumentException("Season numbers must be positive");
        }
        if (end < start) {
            int originalStart = start;
            start = end;
            end = originalStart;
        }
    }

    public boolean contains(int seasonNumber) {
        return seasonNumber >= start && seasonNumber <= end;
    }

    public boolean multiSeason() {
        return start != end;
    }

    public Set<Integer> seasons() {
        return IntStream.rangeClosed(start, end)
                .boxed()
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public String label() {
        if (multiSeason()) {
            return start + "-" + end;
        }
        return Integer.toString(start);
    }

    public String displayName() {
        if (multiSeason()) {
            return "Пак сезонов " + label();
        }
        return "Сезон " + start;
    }
}
