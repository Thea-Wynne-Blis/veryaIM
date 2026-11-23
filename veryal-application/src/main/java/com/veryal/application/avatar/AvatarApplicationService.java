package com.veryal.application.avatar;

import com.veryal.application.file.FileApplicationService;
import com.veryal.application.file.ImageContentValidator;
import com.veryal.application.file.OrphanFiles;
import com.veryal.application.group.GroupAccessGuard;
import com.veryal.application.identity.UserProfile;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.group.GroupRepository;
import com.veryal.domain.group.GroupRole;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * 头像服务：用户与群共用一个实现。
 *
 * <p>头像本体走文件子系统（{@code im_file} + 本地/对象存储），用户表与群表只记
 * {@code avatar_file_id}；读取走公开接口 {@code /api/v1/avatars/{user|group}/{id}}，
 * 这样聊天列表、通讯录、朋友圈都能直接用 {@code <img src>}，不必为每个头像单独带令牌。</p>
 *
 * <p>换头像时旧的 avatar 文件会被清理；清理失败不影响新头像，
 * 残留文件由 {@code StorageCleanupTask} 定时回收。</p>
 */
@Service
@RequiredArgsConstructor
public class AvatarApplicationService {

    /** 前端已裁成 320 见方，4MB 足够兜住未经裁剪的截图。 */
    static final long MAX_AVATAR_BYTES = 4L * 1024 * 1024;
    /** 解码前的像素上限，防止 1 像素炸弹式的超大图。 */
    private static final long MAX_AVATAR_PIXELS = 40_000_000L;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/gif");

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final FileApplicationService fileApplicationService;
    private final GroupAccessGuard groupAccessGuard;
    private final TimeProvider timeProvider;

    @Transactional
    public UserProfile changeUserAvatar(long userId, String filename, String contentType,
                                        byte[] content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String type = ImageContentValidator.validate(contentType, content,
                MAX_AVATAR_BYTES, MAX_AVATAR_PIXELS, ALLOWED_TYPES);
        Long previous = userRepository.findAvatarFileId(userId).orElse(null);
        long fileId = store(userId, filename, type, content);
        Instant now = timeProvider.now();
        String url = "/api/v1/avatars/user/" + userId + "?v=" + now.toEpochMilli();
        userRepository.updateAvatar(userId, url, fileId, now);
        OrphanFiles.dropQuietly(fileApplicationService, previous, fileId);
        return UserProfile.from(user.withAvatar(url, now));
    }

    @Transactional
    public void clearUserAvatar(long userId) {
        Long previous = userRepository.findAvatarFileId(userId).orElse(null);
        userRepository.updateAvatar(userId, null, null, timeProvider.now());
        OrphanFiles.dropQuietly(fileApplicationService, previous, null);
    }

    @Transactional
    public void changeGroupAvatar(long operatorId, String groupId, String filename,
                                  String contentType, byte[] content) {
        groupAccessGuard.requireRole(groupId, operatorId, GroupRole.ADMIN);
        String type = ImageContentValidator.validate(contentType, content,
                MAX_AVATAR_BYTES, MAX_AVATAR_PIXELS, ALLOWED_TYPES);
        Long previous = groupRepository.findAvatarFileId(groupId).orElse(null);
        long fileId = store(operatorId, filename, type, content);
        groupRepository.updateAvatar(groupId, fileId, timeProvider.now());
        OrphanFiles.dropQuietly(fileApplicationService, previous, fileId);
    }

    @Transactional
    public void clearGroupAvatar(long operatorId, String groupId) {
        groupAccessGuard.requireRole(groupId, operatorId, GroupRole.ADMIN);
        Long previous = groupRepository.findAvatarFileId(groupId).orElse(null);
        groupRepository.updateAvatar(groupId, null, timeProvider.now());
        OrphanFiles.dropQuietly(fileApplicationService, previous, null);
    }

    @Transactional(readOnly = true)
    public FileApplicationService.FileStream userAvatar(long userId) {
        return avatarStream(userRepository.findAvatarFileId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "avatar not set")));
    }

    @Transactional(readOnly = true)
    public FileApplicationService.FileStream groupAvatar(String groupId) {
        return avatarStream(groupRepository.findAvatarFileId(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "avatar not set")));
    }

    private FileApplicationService.FileStream avatarStream(long fileId) {
        FileApplicationService.FileStream stream = fileApplicationService.openGranted(fileId);
        String type = stream.file().contentType() == null
                ? "" : stream.file().contentType().toLowerCase(Locale.ROOT);
        // 历史数据里如果混进了非图片文件，按「没有头像」处理，避免公开接口变成任意文件出口。
        if (!type.startsWith("image/") || type.contains("svg")) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "avatar not set");
        }
        return stream;
    }

    private long store(long uploaderId, String filename, String contentType, byte[] content) {
        String name = filename == null || filename.isBlank()
                ? "avatar" + ImageContentValidator.extensionOf(contentType) : filename;
        return fileApplicationService.store(uploaderId, null, name, contentType, content).fileId();
    }
}
