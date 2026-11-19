package com.veryal.application.file;

import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.exception.SystemException;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static final long MAX_FILE_SIZE = 32L * 1024 * 1024;
    /** 图片压缩需要完整解码，超过这个体积直接按原始字节落盘，避免分片合并后又整份进堆。 */
    private static final long IN_MEMORY_COMPRESS_LIMIT = 12L * 1024 * 1024;
    private static final DateTimeFormatter DATE_PATH =
            DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);
    private static final long MAX_NAME_LENGTH = 255;
    private static final long MAX_CONTENT_TYPE_LENGTH = 128;

    private final FileRepository fileRepository;
    private final FileStorageProvider storageProvider;
    private final ConversationRepository conversationRepository;
    private final ImageCompressor imageCompressor;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    /** 缩略图默认最长边；气泡只有 258px 宽，480 足够 2x 屏。 */
    public static final int THUMB_DEFAULT_SIDE = 480;
    /**
     * 主缩略图边长：所有更小的尺寸都从它派生。
     *
     * <p>朋友圈列表最大只用到 640，发布时会异步预热这一档；
     * 之后请求 320 / 480 时只需解码 640 的小图，不用再碰原图。</p>
     */
    public static final int THUMB_MASTER_SIDE = 640;
    private static final int THUMB_MIN_SIDE = 96;
    private static final int THUMB_MAX_SIDE = 1280;
    /** 会被缓存的几种边长，删除文件时按这份清单清理，避免派生文件残留。 */
    private static final int[] CACHED_THUMB_SIDES = {320, THUMB_DEFAULT_SIDE, THUMB_MASTER_SIDE, THUMB_MAX_SIDE};

    public FileView upload(long uploaderId, String filename, String contentType,
                           byte[] content) {
        return upload(uploaderId, null, filename, contentType, content);
    }

    public FileView upload(long uploaderId, String conversationId, String filename,
                           String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "file must not be empty");
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE,
                    "file exceeds max size " + MAX_FILE_SIZE + " bytes");
        }
        return store(uploaderId, conversationId, filename, contentType, content);
    }

    /**
     * 写入文件对象与元数据；供普通上传和分片上传完成时复用。
     */
    public FileView store(long uploaderId, String conversationId, String filename,
                          String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "file must not be empty");
        }
        String safeName = sanitizeName(filename);
        String normalizedType = normalizeContentType(contentType);
        String extension = extension(safeName);
        String scopedConversationId = scopeConversation(uploaderId, conversationId);
        byte[] storedContent = imageCompressor.compress(content, normalizedType);
        Instant now = timeProvider.now();
        long fileId = idGenerator.nextId();
        String objectKey = "files/" + DATE_PATH.format(now) + "/" + fileId + extension;
        String sha256 = sha256(storedContent);
        FileStorage storage = storageProvider.current();
        StoredFile file = new StoredFile(fileId, uploaderId, objectKey, safeName,
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

    public FileContent download(long userId, long fileId) {
        StoredFile file = requireAccessible(userId, fileId);
        byte[] content = storageProvider.get(file.storageType()).get(file.objectKey());
        return new FileContent(file, content);
    }

    /**
     * 校验文件属于指定用户且可用，返回元数据供业务层校验类型（如图片 / 视频）。
     */
    public FileView ownedFile(long userId, long fileId) {
        return FileView.from(requireOwned(userId, fileId));
    }

    /**
     * 打开文件内容流，供下载接口按 Range 分块回写。
     *
     * <p>{@link #download(long, long)} 会把整份内容读进堆，25MB 的视频并发几次就能把内存顶满；
     * 这里只返回存储句柄，由接入层决定读哪一段、读多少。</p>
     */
    public FileStream open(long userId, long fileId) {
        return streamOf(requireAccessible(userId, fileId));
    }

    /**
     * 已由调用方完成业务鉴权（例如朋友圈动态可见性）后打开文件流。
     *
     * <p>只校验文件本身存在且未删除，不再套用「上传者 / 会话成员」规则；
     * 调用方必须先确认当前用户有权访问引用该文件的业务对象。</p>
     */
    public FileStream openGranted(long fileId) {
        return streamOf(requireActive(fileId));
    }

    private FileStream streamOf(StoredFile file) {
        return new FileStream(file, storageProvider.get(file.storageType()));
    }

    /**
     * 把暂存文件流式写入存储，供分片合并使用。
     *
     * <p>只有 JPEG/PNG 且体积可控时才读进内存做压缩；其它类型（视频、音频、压缩包）
     * 全程走流，避免 {@code byte[]} 把整份文件放进堆。</p>
     */
    public FileView storeFile(long uploaderId, String conversationId, String filename,
                              String contentType, Path source, long size) {
        if (size <= 0) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "file must not be empty");
        }
        String normalizedType = normalizeContentType(contentType);
        if (size <= IN_MEMORY_COMPRESS_LIMIT && imageCompressor.supportsCompression(normalizedType)) {
            try {
                return store(uploaderId, conversationId, filename, contentType,
                        Files.readAllBytes(source));
            } catch (IOException e) {
                throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to read staged upload", e);
            }
        }

        String safeName = sanitizeName(filename);
        String extension = extension(safeName);
        String scopedConversationId = scopeConversation(uploaderId, conversationId);
        Instant now = timeProvider.now();
        long fileId = idGenerator.nextId();
        String objectKey = "files/" + DATE_PATH.format(now) + "/" + fileId + extension;
        String sha256 = sha256Of(source);
        FileStorage storage = storageProvider.current();
        StoredFile file = new StoredFile(fileId, uploaderId, objectKey, safeName, normalizedType,
                size, sha256, storage.type(), FileStatus.ACTIVE, now, null, scopedConversationId);

        try (InputStream input = Files.newInputStream(source)) {
            storage.put(objectKey, input, size, normalizedType);
        } catch (IOException e) {
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to read staged upload", e);
        }
        try {
            fileRepository.save(file);
        } catch (RuntimeException e) {
            safeDelete(storage, objectKey);
            throw e;
        }
        return FileView.from(file);
    }

    /**
     * 图片缩略图：首次请求时生成并落盘缓存，后续直接读缓存。
     *
     * <p>同一张图在会话里只显示 258px，原图动辄 2~4MB；
     * 用缩略图能把每次进入会话的图片流量降一到两个数量级。</p>
     */
    public Thumbnail thumbnail(long userId, long fileId, int requestedSide) {
        return buildThumbnail(requireAccessible(userId, fileId), requestedSide);
    }

    /**
     * 已由调用方完成业务鉴权后的缩略图读取。语义同 {@link #openGranted(long)}。
     */
    public Thumbnail thumbnailGranted(long fileId, int requestedSide) {
        return buildThumbnail(requireActive(fileId), requestedSide);
    }

    private Thumbnail buildThumbnail(StoredFile file, int requestedSide) {
        String contentType = file.contentType() == null ? "" : file.contentType().toLowerCase(Locale.ROOT);
        if (!contentType.startsWith("image/")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "thumbnail is only available for images");
        }
        if (contentType.contains("svg")) {
            // SVG 是带脚本能力的 XML 文档，不能当图片回传给浏览器直接打开
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "thumbnail is not available for svg");
        }
        int side = Math.max(THUMB_MIN_SIDE, Math.min(THUMB_MAX_SIDE, requestedSide));
        String thumbKey = file.objectKey() + ".thumb-" + side + ".jpg";
        String etag = file.sha256() + "-t" + side;
        FileStorage storage = storageProvider.get(file.storageType());

        byte[] cached = readQuietly(storage, thumbKey);
        if (cached != null && cached.length > 0) {
            return new Thumbnail(cached, "image/jpeg", etag);
        }
        // 小尺寸优先从主缩略图派生：解码量只有原图的几十分之一。
        byte[] source = side < THUMB_MASTER_SIDE
                ? readQuietly(storage, file.objectKey() + ".thumb-" + THUMB_MASTER_SIDE + ".jpg")
                : null;
        String fallbackType = source == null ? file.contentType() : "image/jpeg";
        if (source == null) {
            source = storage.get(file.objectKey());
        }
        byte[] thumb = imageCompressor.thumbnail(source, side);
        if (thumb == null || thumb.length == 0) {
            // 解码失败（少见格式）直接回退源图，至少能看
            return new Thumbnail(source, fallbackType, etag);
        }
        try {
            storage.put(thumbKey, thumb, "image/jpeg");
        } catch (RuntimeException e) {
            log.warn("failed to cache thumbnail: key={}", thumbKey, e);
        }
        return new Thumbnail(thumb, "image/jpeg", etag);
    }

    private byte[] readQuietly(FileStorage storage, String objectKey) {
        try {
            return storage.get(objectKey);
        } catch (RuntimeException e) {
            // 未缓存或读取失败都按未命中处理，回到原图生成
            return null;
        }
    }

    public FileView get(long userId, long fileId) {
        return FileView.from(requireAccessible(userId, fileId));
    }

    public void delete(long userId, long fileId) {
        StoredFile file = requireOwned(userId, fileId);
        if (file.status() == FileStatus.DELETED) {
            return;
        }
        FileStorage storage = storageProvider.get(file.storageType());
        storage.delete(file.objectKey());
        // 缩略图是按需生成的派生文件，跟着原文件一起清掉（不存在也无妨）
        for (int side : CACHED_THUMB_SIDES) {
            try {
                storage.delete(file.objectKey() + ".thumb-" + side + ".jpg");
            } catch (RuntimeException e) {
                log.debug("thumbnail cleanup skipped: fileId={}, side={}", fileId, side);
            }
        }
        fileRepository.markDeleted(fileId, timeProvider.now());
    }

    /**
     * 返回临时 URL；本地存储没有签名 URL，返回 null 由接入层回退到下载接口。
     */
    public String presignedUrl(long userId, long fileId, Duration ttl) {
        StoredFile file = requireAccessible(userId, fileId);
        return storageProvider.get(file.storageType()).presignedUrl(file.objectKey(), ttl);
    }

    private String scopeConversation(long uploaderId, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND));
        conversationRepository.findMember(conversationId, uploaderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER));
        return conversationId;
    }

    /** 只校验文件存在且未删除，不做任何业务归属判断。 */
    private StoredFile requireActive(long fileId) {
        StoredFile file = fileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found"));
        if (file.status() == FileStatus.DELETED) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND, "file not found");
        }
        return file;
    }

    private StoredFile requireAccessible(long userId, long fileId) {
        StoredFile file = requireActive(fileId);
        if (file.uploaderId() == userId) {
            return file;
        }
        if (file.conversationId() != null
                && conversationRepository.findMember(file.conversationId(), userId).isPresent()) {
            return file;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "file is not accessible");
    }

    private StoredFile requireOwned(long userId, long fileId) {
        StoredFile file = fileRepository.findById(fileId)
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

    private String sha256Of(Path source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(source)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (IOException e) {
            throw new SystemException(ErrorCode.STORAGE_ERROR, "failed to hash staged upload", e);
        }
    }

    public record FileContent(StoredFile file, byte[] content) {
    }

    /**
     * 文件内容读取句柄：调用方按 Range 决定 offset/length，读完负责关闭返回的流。
     */
    public record FileStream(StoredFile file, FileStorage storage) {

        public InputStream open(long offset, long length) {
            return storage.open(file.objectKey(), offset, length);
        }
    }

    public record Thumbnail(byte[] content, String contentType, String etag) {
    }
}
