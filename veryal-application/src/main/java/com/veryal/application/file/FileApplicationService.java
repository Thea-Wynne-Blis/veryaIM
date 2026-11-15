package com.veryal.application.file;

import com.veryal.application.tenant.TenantApplicationService;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.file.FileStatus;
import com.veryal.domain.file.FileStorage;
import com.veryal.domain.file.FileStorageProvider;
import com.veryal.domain.file.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 文件应用服务：上传、下载、删除与临时 URL。
 *
 * <p>文件本体走存储策略，MySQL 只保存元数据；写入对象存储失败或元数据落库失败时做补偿删除。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileApplicationService {

    public static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final DateTimeFormatter DATE_PATH =
            DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);
    private static final long MAX_NAME_LENGTH = 255;
    private static final long MAX_CONTENT_TYPE_LENGTH = 128;

    private final FileRepository fileRepository;
    private final FileStorageProvider storageProvider;
    private final ConversationRepository conversationRepository;
    private final ImageCompressor imageCompressor;
    private final TenantApplicationService tenantApplicationService;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    public FileView upload(long tenantId, long uploaderId, String filename, String contentType,
                           byte[] content) {
        return upload(tenantId, uploaderId, null, filename, contentType, content);
    }

    public FileView upload(long tenantId, long uploaderId, String conversationId, String filename,
                           String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "file must not be empty");
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "file exceeds max size " + MAX_FILE_SIZE + " bytes");
        }
        return store(tenantId, uploaderId, conversationId, filename, contentType, content);
    }

    /**
     * 写入文件对象与元数据；供普通上传和分片上传完成时复用。
     */
    public FileView store(long tenantId, long uploaderId, String conversationId, String filename,
                          String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "file must not be empty");
        }
        String safeName = sanitizeName(filename);
        String normalizedType = normalizeContentType(contentType);
        String extension = extension(safeName);
        String scopedConversationId = scopeConversation(tenantId, uploaderId, conversationId);
        byte[] storedContent = imageCompressor.compress(content, normalizedType);
        tenantApplicationService.assertStorage(tenantId, storedContent.length);
        Instant now = timeProvider.now();
        long fileId = idGenerator.nextId();
        String objectKey = "tenant/" + tenantId + "/" + DATE_PATH.format(now) + "/"
                + fileId + extension;
        String sha256 = sha256(storedContent);
        FileStorage storage = storageProvider.current();
        StoredFile file = new StoredFile(fileId, tenantId, uploaderId, objectKey, safeName,
                normalizedType, storedContent.length, sha256, storage.type(), FileStatus.ACTIVE, now, null,
                scopedConversationId);

        storage.put(objectKey, storedContent, normalizedType);
        try {
            fileRepository.save(file);
        } catch (RuntimeException e) {
            safeDelete(storage, objectKey);
            throw e;
        }
        return FileView.from(file);
    }

    public FileContent download(long tenantId, long userId, long fileId) {
        StoredFile file = requireAccessible(tenantId, userId, fileId);
        byte[] content = storageProvider.get(file.storageType()).get(file.objectKey());
        return new FileContent(file, content);
    }

    public FileView get(long tenantId, long userId, long fileId) {
        return FileView.from(requireAccessible(tenantId, userId, fileId));
    }

    public void delete(long tenantId, long userId, long fileId) {
        StoredFile file = requireOwned(tenantId, userId, fileId);
        if (file.status() == FileStatus.DELETED) {
            return;
        }
        storageProvider.get(file.storageType()).delete(file.objectKey());
        fileRepository.markDeleted(tenantId, fileId, timeProvider.now());
    }

    /**
     * 返回临时 URL；本地存储没有签名 URL，返回 null 由接入层回退到下载接口。
     */
    public String presignedUrl(long tenantId, long userId, long fileId, Duration ttl) {
        StoredFile file = requireAccessible(tenantId, userId, fileId);
        return storageProvider.get(file.storageType()).presignedUrl(file.objectKey(), ttl);
    }

    private String scopeConversation(long tenantId, long uploaderId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        conversationRepository.findById(conversationId)
                .filter(item -> item.tenantId() == tenantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        conversationRepository.findMember(conversationId, uploaderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));
        return conversationId;
    }

    private StoredFile requireAccessible(long tenantId, long userId, long fileId) {
        StoredFile file = fileRepository.findById(tenantId, fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found"));
        if (file.status() == FileStatus.DELETED) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found");
        }
        if (file.uploaderId() == userId) {
            return file;
        }
        if (file.conversationId() != null
                && conversationRepository.findMember(file.conversationId(), userId).isPresent()) {
            return file;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "file is not accessible");
    }

    private StoredFile requireOwned(long tenantId, long userId, long fileId) {
        StoredFile file = fileRepository.findById(tenantId, fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found"));
        if (file.status() == FileStatus.DELETED) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found");
        }
        if (file.uploaderId() != userId) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "only uploader can access file");
        }
        return file;
    }

    private void safeDelete(FileStorage storage, String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException e) {
            log.warn("failed to compensate file object: key={}", objectKey, e);
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
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, (int) MAX_NAME_LENGTH);
        }
        return name;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        String value = contentType.replaceAll("\\p{Cntrl}", "").trim();
        if (value.length() > MAX_CONTENT_TYPE_LENGTH) {
            value = value.substring(0, (int) MAX_CONTENT_TYPE_LENGTH);
        }
        return value.isBlank() ? "application/octet-stream" : value;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,10}") ? "." + ext : "";
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public record FileContent(StoredFile file, byte[] content) {
    }
}
