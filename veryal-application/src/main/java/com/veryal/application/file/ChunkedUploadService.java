package com.veryal.application.file;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.file.UploadRepository;
import com.veryal.domain.file.UploadSession;
import com.veryal.domain.file.UploadStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 分片上传：会话元数据落库，分片暂存本地，完成后合并并写入文件存储。
 *
 * <p>分片暂存目录按 uploadId 隔离；同一分片重复上传幂等覆盖。当前分片暂存为单机目录，
 * 跨节点续传需共享存储（后续可切换为对象存储分片）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkedUploadService {

    private static final int MAX_CHUNKS = 10_000;

    private final UploadRepository uploadRepository;
    private final FileApplicationService fileApplicationService;
    private final UploadProperties properties;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public UploadSessionView init(long uploaderId, String filename, String contentType,
                                  long sizeBytes, String conversationId) {
        if (sizeBytes <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "sizeBytes must be positive");
        }
        if (sizeBytes > properties.getMaxSizeBytes()) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "file exceeds chunked upload limit " + properties.getMaxSizeBytes());
        }
        int chunkSize = Math.max(1, properties.getChunkSizeBytes());
        int totalChunks = (int) ((sizeBytes + chunkSize - 1) / chunkSize);
        if (totalChunks <= 0 || totalChunks > MAX_CHUNKS) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid chunk count");
        }
        long uploadId = idGenerator.nextId();
        UploadSession session = new UploadSession(uploadId, uploaderId,
                sanitizeName(filename), normalizeType(contentType), sizeBytes, chunkSize, totalChunks,
                blankToNull(conversationId), UploadStatus.ACTIVE, timeProvider.now(), null);
        uploadRepository.save(session);
        try {
            Files.createDirectories(chunkDir(uploadId));
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "failed to create upload staging dir");
        }
        return view(session);
    }

    public UploadSessionView uploadChunk(long uploaderId, long uploadId, int index,
                                         byte[] chunk) {
        UploadSession session = requireActive(uploaderId, uploadId);
        if (index < 0 || index >= session.totalChunks()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "chunk index out of range");
        }
        int expected = expectedSize(session, index);
        if (chunk == null || chunk.length != expected) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "chunk size mismatch: expected " + expected);
        }
        Path target = chunkDir(uploadId).resolve(index + ".part");
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), "chunk-", ".tmp");
            Files.write(temp, chunk);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "failed to store upload chunk");
        }
        return view(session);
    }

    public UploadSessionView status(long uploaderId, long uploadId) {
        UploadSession session = requireOwned(uploaderId, uploadId);
        return view(session);
    }

    public FileView complete(long uploaderId, long uploadId) {
        UploadSession session = requireActive(uploaderId, uploadId);
        List<Integer> uploaded = uploadedIndices(uploadId);
        if (uploaded.size() != session.totalChunks()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "missing chunks: uploaded " + uploaded.size() + "/" + session.totalChunks());
        }
        Path dir = chunkDir(uploadId);
        Path merged = dir.resolve("merged.tmp");
        try {
            // 逐个分片追加到暂存文件，避免 25MB 视频又拼出一个 25MB 的 byte[]
            try (var out = Files.newOutputStream(merged,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE)) {
                for (int index = 0; index < session.totalChunks(); index++) {
                    Path chunk = dir.resolve(index + ".part");
                    if (!Files.isRegularFile(chunk)
                            || Files.size(chunk) != expectedSize(session, index)) {
                        throw new BusinessException(ErrorCode.CONFLICT,
                                "chunk " + index + " is invalid");
                    }
                    Files.copy(chunk, out);
                }
            }
            long assembledSize = Files.size(merged);
            if (assembledSize != session.sizeBytes()) {
                throw new BusinessException(ErrorCode.CONFLICT, "assembled size mismatch");
            }
            FileView file = fileApplicationService.storeFile(uploaderId, session.conversationId(),
                    session.filename(), session.contentType(), merged, assembledSize);
            uploadRepository.markStatus(uploadId, UploadStatus.COMPLETED, file.fileId());
            return file;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "failed to assemble upload chunks");
        } finally {
            cleanup(uploadId);
        }
    }

    public void cancel(long uploaderId, long uploadId) {
        UploadSession session = requireOwned(uploaderId, uploadId);
        if (session.status() == UploadStatus.CANCELED) {
            return;
        }
        if (session.status() == UploadStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.CONFLICT, "upload already completed");
        }
        uploadRepository.markStatus(uploadId, UploadStatus.CANCELED, null);
        cleanup(uploadId);
    }

    private UploadSession requireActive(long uploaderId, long uploadId) {
        UploadSession session = requireOwned(uploaderId, uploadId);
        if (session.status() != UploadStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CONFLICT, "upload session is not active");
        }
        return session;
    }

    private UploadSession requireOwned(long uploaderId, long uploadId) {
        UploadSession session = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "upload session not found"));
        if (session.uploaderId() != uploaderId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "upload session does not belong to user");
        }
        return session;
    }

    private UploadSessionView view(UploadSession session) {
        return new UploadSessionView(session.id(), session.chunkSize(), session.totalChunks(),
                uploadedIndices(session.id()), session.status().name(), session.fileId());
    }

    private List<Integer> uploadedIndices(long uploadId) {
        Path dir = chunkDir(uploadId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Integer> indices = new ArrayList<>();
        try (var files = Files.list(dir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".part"))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        try {
                            indices.add(Integer.parseInt(name.substring(0, name.length() - 5)));
                        } catch (NumberFormatException ignored) {
                            // 忽略临时文件
                        }
                    });
        } catch (IOException e) {
            log.warn("failed to list upload chunks: uploadId={}", uploadId, e);
        }
        indices.sort(Comparator.naturalOrder());
        return indices;
    }

    private int expectedSize(UploadSession session, int index) {
        if (index < session.totalChunks() - 1) {
            return session.chunkSize();
        }
        long consumed = (long) session.chunkSize() * (session.totalChunks() - 1);
        return (int) (session.sizeBytes() - consumed);
    }

    private Path chunkDir(long uploadId) {
        return Paths.get(properties.getStagingPath()).toAbsolutePath().normalize()
                .resolve(Long.toString(uploadId));
    }

    private void cleanup(long uploadId) {
        Path dir = chunkDir(uploadId);
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("failed to delete upload staging file: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("failed to cleanup upload staging dir: {}", dir, e);
        }
    }

    private String sanitizeName(String filename) {
        String name = filename == null ? "" : filename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("\\p{Cntrl}", "").trim();
        if (name.isBlank()) {
            name = "file";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private String normalizeType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.length() > 128 ? contentType.substring(0, 128) : contentType;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record UploadSessionView(long uploadId, int chunkSize, int totalChunks,
                                    List<Integer> uploadedChunks, String status, Long fileId) {
    }
}
