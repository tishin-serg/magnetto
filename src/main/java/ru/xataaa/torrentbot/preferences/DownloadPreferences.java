package ru.xataaa.torrentbot.preferences;

import java.math.BigDecimal;
import ru.xataaa.torrentbot.job.DownloadTarget;
import ru.xataaa.torrentbot.torrentsearch.TorrentSearchResult;

public record DownloadPreferences(long minBytes, long maxBytes, int minSeeders, DownloadTarget target,
                                  long minDownloadSpeedBytesPerSecond, boolean autoReplaceSlowDownload) {
    public static final long GB = 1_000_000_000L;

    public DownloadPreferences {
        if (minBytes < 0 || maxBytes <= 0 || minBytes > maxBytes)
            throw new IllegalArgumentException("Размер «от» должен быть не больше «до». Минимум — 0, максимум — больше 0.");
        if (minSeeders < 1) throw new IllegalArgumentException("Укажи минимум 1 сид.");
        if (minDownloadSpeedBytesPerSecond < 0) throw new IllegalArgumentException("Минимальная скорость не может быть отрицательной.");
        if (target == null || target == DownloadTarget.S3_LATER)
            throw new IllegalArgumentException("Выбери домашний ПК, VPS или S3.");
    }

    public DownloadPreferences(long minBytes, long maxBytes, int minSeeders, DownloadTarget target) {
        this(minBytes, maxBytes, minSeeders, target, 0, false);
    }

    public static DownloadPreferences defaults() {
        return new DownloadPreferences(4 * GB, 15 * GB, 10, DownloadTarget.HOME_PC, 0, false);
    }

    public boolean accepts(TorrentSearchResult result) {
        return result.hasMagnet() && result.sizeBytes() > 0
                && result.sizeBytes() >= minBytes && result.sizeBytes() <= maxBytes
                && result.seeders() >= minSeeders;
    }

    public DownloadPreferences with(String field, String value) {
        try {
            return switch (field) {
                case "min" -> new DownloadPreferences(bytes(value), maxBytes, minSeeders, target, minDownloadSpeedBytesPerSecond, autoReplaceSlowDownload);
                case "max" -> new DownloadPreferences(minBytes, bytes(value), minSeeders, target, minDownloadSpeedBytesPerSecond, autoReplaceSlowDownload);
                case "seeds" -> new DownloadPreferences(minBytes, maxBytes, Integer.parseInt(value.trim()), target, minDownloadSpeedBytesPerSecond, autoReplaceSlowDownload);
                case "target" -> new DownloadPreferences(minBytes, maxBytes, minSeeders, DownloadTarget.valueOf(value), minDownloadSpeedBytesPerSecond, autoReplaceSlowDownload);
                case "speed" -> new DownloadPreferences(minBytes, maxBytes, minSeeders, target, speedBytes(value), autoReplaceSlowDownload);
                case "auto" -> new DownloadPreferences(minBytes, maxBytes, minSeeders, target, minDownloadSpeedBytesPerSecond, Boolean.parseBoolean(value));
                default -> throw new IllegalArgumentException("Неизвестная настройка.");
            };
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException("Введи корректное число. Размер можно указать дробным, например 4,5.");
        }
    }

    private static long bytes(String value) {
        return new BigDecimal(value.trim().replace(',', '.')).multiply(BigDecimal.valueOf(GB)).longValueExact();
    }

    private static long speedBytes(String value) {
        return new BigDecimal(value.trim().replace(',', '.')).multiply(BigDecimal.valueOf(1_000_000L)).longValueExact();
    }

    public String summary() {
        return "Размер: от " + gb(minBytes) + " до " + gb(maxBytes) + " ГБ\nМинимум сидов: "
                + minSeeders + "\nКуда: " + targetLabel()
                + "\nМинимальная скорость: " + (minDownloadSpeedBytesPerSecond == 0 ? "выключено" : mbps(minDownloadSpeedBytesPerSecond) + " МБ/с")
                + "\nАвтозамена медленной раздачи: " + (autoReplaceSlowDownload ? "включена" : "выключена");
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

    public static String mbps(long bytesPerSecond) {
        return BigDecimal.valueOf(bytesPerSecond, 6).stripTrailingZeros().toPlainString();
    }
}
