package ru.xataaa.torrentbot.preferences;

import java.math.BigDecimal;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchResult;

public record DownloadPreferences(long minBytes, long maxBytes, int minSeeders, DownloadTarget target) {
    public static final long GB = 1_000_000_000L;

    public DownloadPreferences {
        if (minBytes < 0 || maxBytes <= 0 || minBytes > maxBytes)
            throw new IllegalArgumentException("Размер «от» должен быть не больше «до». Минимум — 0, максимум — больше 0.");
        if (minSeeders < 1) throw new IllegalArgumentException("Укажи минимум 1 сид.");
        if (target == null || target == DownloadTarget.S3_LATER)
            throw new IllegalArgumentException("Выбери домашний ПК, VPS или S3.");
    }

    public static DownloadPreferences defaults() {
        return new DownloadPreferences(4 * GB, 15 * GB, 10, DownloadTarget.HOME_PC);
    }

    public boolean accepts(TorrentSearchResult result) {
        return result.hasMagnet() && result.sizeBytes() > 0
                && result.sizeBytes() >= minBytes && result.sizeBytes() <= maxBytes
                && result.seeders() >= minSeeders;
    }

    public DownloadPreferences with(String field, String value) {
        try {
            return switch (field) {
                case "min" -> new DownloadPreferences(bytes(value), maxBytes, minSeeders, target);
                case "max" -> new DownloadPreferences(minBytes, bytes(value), minSeeders, target);
                case "seeds" -> new DownloadPreferences(minBytes, maxBytes, Integer.parseInt(value.trim()), target);
                case "target" -> new DownloadPreferences(minBytes, maxBytes, minSeeders, DownloadTarget.valueOf(value));
                default -> throw new IllegalArgumentException("Неизвестная настройка.");
            };
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Введи корректное число. Размер можно указать дробным, например 4,5.");
        }
    }

    private static long bytes(String value) {
        return new BigDecimal(value.trim().replace(',', '.')).multiply(BigDecimal.valueOf(GB)).longValueExact();
    }

    public String summary() {
        return "Размер: от " + gb(minBytes) + " до " + gb(maxBytes) + " ГБ\nМинимум сидов: "
                + minSeeders + "\nКуда: " + targetLabel();
    }

    public String targetLabel() {
        return switch (target) {
            case HOME_PC -> "Домашний ПК";
            case VPS -> "VPS";
            case S3, S3_LATER -> "S3 (через VPS)";
        };
    }

    public static String gb(long bytes) {
        return BigDecimal.valueOf(bytes, 9).stripTrailingZeros().toPlainString();
    }
}