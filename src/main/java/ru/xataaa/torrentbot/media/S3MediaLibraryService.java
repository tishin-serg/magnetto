package ru.xataaa.torrentbot.media;

import java.net.URI;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.xataaa.torrentbot.common.ErrorCode;
import ru.xataaa.torrentbot.config.S3MediaProperties;
import ru.xataaa.torrentbot.retry.NonRetryableOperationException;
import ru.xataaa.torrentbot.retry.RetryableOperationException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3MediaLibraryService {

    private static final int DELETE_BATCH_SIZE = 1000;

    private final S3MediaProperties properties;

    public boolean isEnabled() {
        return properties.enabled();
    }

    public boolean isConfigured() {
        return properties.bucket() != null && !properties.bucket().isBlank()
                && properties.accessKey() != null && !properties.accessKey().isBlank()
                && properties.secretKey() != null && !properties.secretKey().isBlank();
    }

    public long ttlHours() {
        return properties.presignedLinkTtlHours();
    }

    public boolean deleteLocalAfterUpload() {
        return properties.deleteLocalAfterUpload();
    }

    public S3UploadResult upload(Path sourcePath, String originalFileName, String existingObjectKey, long expectedSizeBytes) {
        assertEnabled();
        if (existingObjectKey != null && !existingObjectKey.isBlank()) {
            requireSafeObjectKey(existingObjectKey);
            if (objectExistsWithSize(existingObjectKey, expectedSizeBytes)) {
                return new S3UploadResult(existingObjectKey, fileNameFromKey(existingObjectKey), true);
            }
        }
        String objectKey = uniqueObjectKey(originalFileName, expectedSizeBytes);
        try (S3Client client = s3Client()) {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.bucket())
                            .key(objectKey)
                            .build(),
                    RequestBody.fromFile(sourcePath)
            );
            log.info("Uploaded file to S3 media library: key={}, sourceFile={}", objectKey, sourcePath.getFileName());
            return new S3UploadResult(objectKey, fileNameFromKey(objectKey), false);
        } catch (RuntimeException exception) {
            throw retryable(ErrorCode.S3_UPLOAD_FAILED, "Failed to upload file to S3", exception);
        }
    }

    public List<S3MediaLibraryFile> listFiles() {
        if (!properties.enabled()) {
            return List.of();
        }
        List<S3MediaLibraryFile> files = new ArrayList<>();
        String continuationToken = null;
        try (S3Client client = s3Client()) {
            do {
                ListObjectsV2Request.Builder request = ListObjectsV2Request.builder()
                        .bucket(properties.bucket())
                        .prefix(properties.prefix())
                        .continuationToken(continuationToken);
                var response = client.listObjectsV2(request.build());
                for (S3Object object : response.contents()) {
                    if (object.key().endsWith("/") || object.size() <= 0) {
                        continue;
                    }
                    files.add(new S3MediaLibraryFile(
                            fileNameFromKey(object.key()),
                            object.key(),
                            object.size(),
                            object.lastModified() == null ? null : LocalDateTime.ofInstant(object.lastModified(), ZoneId.systemDefault())
                    ));
                }
                continuationToken = response.nextContinuationToken();
            } while (continuationToken != null && !continuationToken.isBlank());
        } catch (RuntimeException exception) {
            throw retryable(ErrorCode.S3_UNAVAILABLE, "Failed to list S3 media library", exception);
        }
        files.sort(Comparator.comparing(S3MediaLibraryFile::modifiedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return files;
    }

    public String createPresignedUrl(String objectKey) {
        assertEnabled();
        requireSafeObjectKey(objectKey);
        try (S3Presigner presigner = s3Presigner()) {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build();
            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofHours(properties.presignedLinkTtlHours()))
                    .getObjectRequest(getObjectRequest)
                    .build();
            return presigner.presignGetObject(presignRequest).url().toString();
        } catch (RuntimeException exception) {
            throw retryable(ErrorCode.S3_UNAVAILABLE, "Failed to create S3 download link", exception);
        }
    }

    public void deleteFile(String objectKey) {
        assertEnabled();
        requireSafeObjectKey(objectKey);
        try (S3Client client = s3Client()) {
            client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build());
        } catch (RuntimeException exception) {
            throw retryable(ErrorCode.S3_DELETE_FAILED, "Failed to delete S3 object", exception);
        }
    }

    public MediaLibraryCleanupResult cleanupAllFiles() {
        assertEnabled();
        long deletedBytes = 0L;
        int deletedFiles = 0;
        List<ObjectIdentifier> batch = new ArrayList<>(DELETE_BATCH_SIZE);
        String continuationToken = null;
        try (S3Client client = s3Client()) {
            do {
                var response = client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(properties.bucket())
                        .prefix(properties.prefix())
                        .continuationToken(continuationToken)
                        .build());
                for (S3Object object : response.contents()) {
                    if (object.key().endsWith("/")) {
                        continue;
                    }
                    requireSafeObjectKey(object.key());
                    deletedBytes += object.size();
                    deletedFiles++;
                    batch.add(ObjectIdentifier.builder().key(object.key()).build());
                    if (batch.size() == DELETE_BATCH_SIZE) {
                        deleteBatch(client, batch);
                        batch.clear();
                    }
                }
                continuationToken = response.nextContinuationToken();
            } while (continuationToken != null && !continuationToken.isBlank());
            if (!batch.isEmpty()) {
                deleteBatch(client, batch);
            }
            return new MediaLibraryCleanupResult(deletedFiles, deletedBytes);
        } catch (RuntimeException exception) {
            throw retryable(ErrorCode.S3_DELETE_FAILED, "Failed to cleanup S3 media library", exception);
        }
    }

    public String fileKey(S3MediaLibraryFile file) {
        return shortHash(file.objectKey());
    }

    public S3MediaLibraryFile findFileByKey(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return null;
        }
        for (S3MediaLibraryFile file : listFiles()) {
            if (fileKey(file).equals(fileKey)) {
                return file;
            }
        }
        return null;
    }

    public void requireSafeObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new NonRetryableOperationException(ErrorCode.UNKNOWN_ERROR, "S3 object key is empty");
        }
        String normalized = objectKey.replace("\\", "/");
        if (!normalized.equals(objectKey)
                || normalized.contains("..")
                || normalized.chars().anyMatch(character -> character < 32)
                || !normalized.startsWith(properties.prefix())
                || normalized.equals(properties.prefix())) {
            throw new NonRetryableOperationException(ErrorCode.UNKNOWN_ERROR, "Unsafe S3 object key");
        }
    }

    private String uniqueObjectKey(String originalFileName, long expectedSizeBytes) {
        String safeFileName = safeFileName(originalFileName);
        String baseName = baseName(safeFileName);
        String extension = extension(safeFileName);
        String candidate = properties.prefix() + safeFileName;
        int suffix = 1;
        while (objectExistsWithDifferentSize(candidate, expectedSizeBytes)) {
            candidate = properties.prefix() + baseName + " (" + suffix + ")" + extension;
            suffix++;
        }
        return candidate;
    }

    private boolean objectExistsWithDifferentSize(String objectKey, long expectedSizeBytes) {
        try (S3Client client = s3Client()) {
            long currentSize = client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build()).contentLength();
            return currentSize != expectedSizeBytes;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw retryable(ErrorCode.S3_UNAVAILABLE, "Failed to inspect S3 object", exception);
        }
    }

    private boolean objectExistsWithSize(String objectKey, long expectedSizeBytes) {
        try (S3Client client = s3Client()) {
            long currentSize = client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(objectKey)
                    .build()).contentLength();
            return currentSize == expectedSizeBytes;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw retryable(ErrorCode.S3_UNAVAILABLE, "Failed to inspect S3 object", exception);
        }
    }

    private void deleteBatch(S3Client client, List<ObjectIdentifier> batch) {
        client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(properties.bucket())
                .delete(Delete.builder().objects(batch).build())
                .build());
    }

    private S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccessEnabled())
                        .build());
        if (properties.endpointUrl() != null && !properties.endpointUrl().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpointUrl()));
        }
        return builder.build();
    }

    private S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccessEnabled())
                        .build());
        if (properties.endpointUrl() != null && !properties.endpointUrl().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpointUrl()));
        }
        return builder.build();
    }

    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }

    private void assertEnabled() {
        if (!properties.enabled()) {
            throw new NonRetryableOperationException(ErrorCode.S3_UNAVAILABLE, "S3 media library is disabled");
        }
        if (!isConfigured()) {
            throw new NonRetryableOperationException(ErrorCode.S3_UNAVAILABLE, "S3 media library is not configured");
        }
    }

    private RetryableOperationException retryable(ErrorCode errorCode, String message, Throwable throwable) {
        return new RetryableOperationException(errorCode, message + ": " + throwable.getMessage(), throwable);
    }

    private String safeFileName(String originalFileName) {
        String value = originalFileName == null ? "" : originalFileName;
        value = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace("\\", "_")
                .replace("/", "_")
                .replaceAll("[\\p{Cntrl}]+", "")
                .replace("..", ".")
                .trim();
        while (value.startsWith(".")) {
            value = value.substring(1);
        }
        if (value.isBlank()) {
            return "video";
        }
        return value;
    }

    private String fileNameFromKey(String objectKey) {
        int slashIndex = objectKey.lastIndexOf('/');
        return slashIndex >= 0 ? objectKey.substring(slashIndex + 1) : objectKey;
    }

    private String baseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String extension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex) : "";
    }

    private String shortHash(String value) {
        return Integer.toUnsignedString(value.toLowerCase(Locale.ROOT).hashCode(), 36);
    }
}
