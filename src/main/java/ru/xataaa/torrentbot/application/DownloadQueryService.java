package ru.xataaa.torrentbot.application;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.file.DownloadFile;
import ru.xataaa.torrentbot.file.DownloadFileRepository;
import ru.xataaa.torrentbot.job.DownloadJob;
import ru.xataaa.torrentbot.job.DownloadJobRepository;

@Service
@RequiredArgsConstructor
public class DownloadQueryService {
    private final DownloadJobRepository jobs;
    private final DownloadFileRepository files;
    public List<DownloadJob> recent(Long chatId, int limit) { return jobs.findRecent(limit).stream().filter(j -> chatId.equals(j.getChatId())).toList(); }
    public DownloadJob owned(UUID id, Long chatId) { return jobs.findById(id).filter(j -> chatId.equals(j.getChatId())).orElse(null); }
    public List<DownloadFile> files(UUID id) { return files.findByJobId(id); }
}
