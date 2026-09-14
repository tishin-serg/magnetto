package ru.xataaa.torrentbot.application;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.file.DownloadFile;
import ru.xataaa.torrentbot.file.DownloadFileRepository;
import ru.xataaa.torrentbot.file.DownloadFileStatus;
import ru.xataaa.torrentbot.job.DownloadJob;

/** Shared, idempotent persistence part of file confirmation used by API and UI adapters. */
@Service
@RequiredArgsConstructor
public class DownloadFileSelectionService {
    private final DownloadFileRepository files;

    public synchronized List<DownloadFile> confirm(DownloadJob job, List<UUID> selectedIds) {
        List<UUID> selected = selectedIds == null ? List.of() : List.copyOf(selectedIds);
        List<DownloadFile> all = files.findByJobId(job.getId());
        List<DownloadFile> chosen = all.stream().filter(file -> selected.contains(file.getId())).toList();
        if (chosen.isEmpty()) throw new IllegalArgumentException("At least one file is required");
        all.forEach(file -> files.updateStatus(file.getId(), selected.contains(file.getId())
                ? DownloadFileStatus.READY_TO_UPLOAD : DownloadFileStatus.SKIPPED_BY_USER));
        return chosen;
    }
}
