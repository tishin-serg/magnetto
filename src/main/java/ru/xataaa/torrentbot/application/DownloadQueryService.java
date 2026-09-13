package ru.xataaa.torrentbot.application;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.file.DownloadFile;
import ru.xataaa.torrentbot.file.DownloadFileRepository;
import ru.xataaa.torrentbot.downloadlink.DownloadLinkService;
import ru.xataaa.torrentbot.media.S3MediaLibraryService;
import ru.xataaa.torrentbot.job.DownloadJob;
import ru.xataaa.torrentbot.job.DownloadJobRepository;

@Service
@RequiredArgsConstructor
public class DownloadQueryService {
    private final DownloadJobRepository jobs;
    private final DownloadFileRepository files;
    private final DownloadLinkService links;
    private final S3MediaLibraryService s3;
    public List<DownloadJob> recent(Long chatId, int limit) { return jobs.findRecent(limit).stream().filter(j -> chatId.equals(j.getChatId())).toList(); }
    public DownloadJob owned(UUID id, Long chatId) { return jobs.findById(id).filter(j -> chatId.equals(j.getChatId())).orElse(null); }
    public List<DownloadFile> files(UUID id) { return files.findByJobId(id); }
    public List<String> readyLinks(UUID id) {
        return files(id).stream().flatMap(file -> {
            if (file.getS3ObjectKey() != null && !file.getS3ObjectKey().isBlank() && s3.isConfigured())
                return java.util.stream.Stream.of(s3.createPresignedUrl(file.getS3ObjectKey()));
            return links.findActiveLinkForFile(file.getId()).map(link -> links.publicUrl(link)).stream();
        }).toList();
    }
}
