package com.veryal.application.chatbackground;

import com.veryal.application.file.FileApplicationService;
import com.veryal.application.file.ImageContentValidator;
import com.veryal.application.file.OrphanFiles;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.domain.chatbackground.ChatBackground;
import com.veryal.domain.chatbackground.ChatBackgroundPresets;
import com.veryal.domain.chatbackground.ChatBackgroundRepository;
import com.veryal.domain.conversation.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 聊天背景服务。
 *
 * <p>两种作用域：不传会话 ID 改的是「全局背景」，传了就是该会话的覆盖。
 * 会话级为空时前端自动回退到全局。每个用户的设置互相独立；上传的图片
 * 不绑定会话，只有上传者本人能通过文件接口读取。</p>
 */
@Service
@RequiredArgsConstructor
public class ChatBackgroundApplicationService {

    /** 前端已经压到 1920px，8MB 足够兜住未经压缩的截图。 */
    static final long MAX_BACKGROUND_BYTES = 8L * 1024 * 1024;
    /** 解码前的像素上限，防止 1 像素炸弹式的超大图。 */
    private static final long MAX_BACKGROUND_PIXELS = 60_000_000L;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ChatBackgroundRepository backgroundRepository;
    private final ConversationRepository conversationRepository;
    private final FileApplicationService fileApplicationService;

    @Transactional(readOnly = true)
    public ChatBackgroundView view(long userId) {
        return new ChatBackgroundView(
                backgroundRepository.findGlobal(userId).orElse(ChatBackground.none()),
                backgroundRepository.findConversations(userId));
    }

    /** 选一个内置渐变；返回本次生效的背景。 */
    @Transactional
    public ChatBackground setPreset(long userId, String conversationId, String preset) {
        String id = ChatBackgroundPresets.normalize(preset);
        if (!ChatBackgroundPresets.isKnown(id)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "不支持的聊天背景预设");
        }
        ChatBackground next = ChatBackground.preset(id);
        ChatBackground previous = persist(userId, conversationId, next);
        OrphanFiles.dropQuietly(fileApplicationService, previous.fileId(), null);
        return next;
    }

    /** 清除背景；会话级清除后回退到全局。 */
    @Transactional
    public ChatBackground clear(long userId, String conversationId) {
        ChatBackground previous = persist(userId, conversationId, ChatBackground.none());
        OrphanFiles.dropQuietly(fileApplicationService, previous.fileId(), null);
        return ChatBackground.none();
    }

    /** 上传一张自定义背景图，替换同作用域之前的图片。 */
    @Transactional
    public ChatBackground upload(long userId, String conversationId, String filename,
                                 String contentType, byte[] content) {
        requireConversationMember(userId, conversationId);
        String type = ImageContentValidator.validate(contentType, content,
                MAX_BACKGROUND_BYTES, MAX_BACKGROUND_PIXELS, ALLOWED_TYPES);
        long fileId = fileApplicationService.store(userId, null,
                filenameOf(filename, type), type, content).fileId();
        ChatBackground next = ChatBackground.image(fileId);
        ChatBackground previous = persist(userId, conversationId, next);
        OrphanFiles.dropQuietly(fileApplicationService, previous.fileId(), fileId);
        return next;
    }

    /** 写入并返回该作用域原来的选择，用于回收旧图。 */
    private ChatBackground persist(long userId, String conversationId, ChatBackground next) {
        if (!isScoped(conversationId)) {
            ChatBackground previous = backgroundRepository.findGlobal(userId)
                    .orElse(ChatBackground.none());
            backgroundRepository.saveGlobal(userId, next);
            return previous;
        }
        requireConversationMember(userId, conversationId);
        ChatBackground previous = backgroundRepository.findConversation(conversationId, userId)
                .orElse(ChatBackground.none());
        backgroundRepository.saveConversation(conversationId, userId, next);
        return previous;
    }

    private void requireConversationMember(long userId, String conversationId) {
        if (!isScoped(conversationId)) {
            return;
        }
        if (conversationRepository.findMember(conversationId, userId).isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_CONVERSATION_MEMBER);
        }
    }

    private static boolean isScoped(String conversationId) {
        return conversationId != null && !conversationId.isBlank();
    }

    private static String filenameOf(String filename, String type) {
        if (filename != null && !filename.isBlank()) {
            return filename;
        }
        return "chat-background" + ImageContentValidator.extensionOf(type);
    }
}
