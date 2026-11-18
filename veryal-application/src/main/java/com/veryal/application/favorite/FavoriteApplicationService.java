package com.veryal.application.favorite;

import com.fasterxml.jackson.databind.JsonNode;
import com.veryal.application.conversation.ConversationApplicationService;
import com.veryal.application.conversation.ConversationCard;
import com.veryal.common.core.TimeProvider;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.id.IdGenerator;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.favorite.Favorite;
import com.veryal.domain.favorite.FavoriteRepository;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.file.StoredFile;
import com.veryal.domain.friend.Friend;
import com.veryal.domain.friend.FriendRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.message.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 收藏应用服务。
 *
 * <p>收藏保存的是消息快照：原消息撤回或会话被删掉后，收藏列表仍然展示当时的
 * 文字 / 文件名。文件本体通过 {@code fileId} 继续从文件中心读取。</p>
 */
@Service
@RequiredArgsConstructor
public class FavoriteApplicationService {

    private static final int MAX_LIST_SIZE = 200;

    private final FavoriteRepository favoriteRepository;
    private final MessageRepository messageRepository;
    private final FileRepository fileRepository;
    private final FriendRepository friendRepository;
    private final UserRepository userRepository;
    private final ConversationApplicationService conversationApplicationService;
    private final IdGenerator idGenerator;
    private final TimeProvider timeProvider;

    @Transactional
    public FavoriteView add(long userId, String conversationId, long messageId) {
        conversationApplicationService.requireMember(conversationId, userId);
        Message message = messageRepository.findById(messageId)
                .filter(item -> item.conversationId().equals(conversationId))
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));
        Favorite existing = favoriteRepository.find(userId, messageId).orElse(null);
        if (existing != null) {
            return view(existing);
        }

        JsonNode payload = payloadOf(message);
        long fileId = payload.path("fileId").asLong(0);
        StoredFile file = fileId > 0 ? fileRepository.findById(fileId).orElse(null) : null;
        String fileName = text(payload, "fileName");
        if (fileName == null) {
            fileName = text(payload, "name");
        }
        if (fileName == null && file != null) {
            fileName = file.originalName();
        }
        Long sizeBytes = null;
        if (file != null) {
            sizeBytes = file.sizeBytes();
        } else if (payload.has("sizeBytes") && payload.path("sizeBytes").canConvertToLong()) {
            sizeBytes = payload.path("sizeBytes").asLong();
        }
        String contentType = file != null ? file.contentType() : text(payload, "contentType");

        Instant now = timeProvider.now();
        Favorite favorite = new Favorite(idGenerator.nextId(), userId, conversationId,
                message.id(), message.type().code(), message.content(),
                fileId > 0 ? fileId : null, fileName, sizeBytes, contentType,
                message.senderId(), senderNameOf(userId, message.senderId()),
                conversationTitleOf(userId, conversationId), now);
        try {
            favoriteRepository.save(favorite);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 并发双击收藏：返回已有的那条。
            return favoriteRepository.find(userId, messageId).map(this::view).orElseThrow(() -> e);
        }
        return view(favorite);
    }

    @Transactional(readOnly = true)
    public List<FavoriteView> list(long userId, String keyword, int limit) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        int size = limit <= 0 ? MAX_LIST_SIZE : Math.min(limit, MAX_LIST_SIZE);
        return favoriteRepository.listByUser(userId, size).stream()
                .filter(favorite -> query.isEmpty() || matches(favorite, query))
                .map(this::view)
                .toList();
    }

    @Transactional
    public void remove(long userId, long favoriteId) {
        favoriteRepository.findById(favoriteId)
                .filter(favorite -> favorite.userId() == userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "favorite not found"));
        favoriteRepository.delete(userId, favoriteId);
    }

    private boolean matches(Favorite favorite, String query) {
        return contains(favorite.fileName(), query)
                || contains(favorite.senderName(), query)
                || contains(favorite.conversationTitle(), query)
                || contains(textOf(favorite), query);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase().contains(query);
    }

    private String textOf(Favorite favorite) {
        return favorite.msgType() == MessageType.TEXT.code()
                || favorite.msgType() == MessageType.EMOJI.code()
                ? new String(favorite.content(), StandardCharsets.UTF_8)
                : null;
    }

    private FavoriteView view(Favorite favorite) {
        MessageType type;
        try {
            type = MessageType.fromCode(favorite.msgType());
        } catch (RuntimeException e) {
            type = MessageType.TEXT;
        }
        String text = type == MessageType.TEXT
                ? new String(favorite.content(), StandardCharsets.UTF_8)
                : type == MessageType.EMOJI ? "表情" : null;
        return new FavoriteView(favorite.id(), favorite.conversationId(), favorite.messageId(),
                type.name(), text, favorite.fileId(), favorite.fileName(), favorite.sizeBytes(),
                favorite.contentType(), favorite.senderId(), favorite.senderName(),
                favorite.conversationTitle(), favorite.createdAt());
    }

    private JsonNode payloadOf(Message message) {
        if (message.type() == MessageType.TEXT || message.type() == MessageType.EMOJI) {
            return JsonUtils.mapper().createObjectNode();
        }
        try {
            JsonNode node = JsonUtils.mapper().readTree(message.content());
            return node == null || !node.isObject() ? JsonUtils.mapper().createObjectNode() : node;
        } catch (Exception e) {
            return JsonUtils.mapper().createObjectNode();
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    private String senderNameOf(long userId, long senderId) {
        if (senderId == userId) {
            return "我";
        }
        String remark = friendRepository.find(userId, senderId)
                .map(Friend::remark)
                .filter(value -> value != null && !value.isBlank())
                .orElse(null);
        if (remark != null) {
            return remark;
        }
        return userRepository.findById(senderId).map(User::nickname).orElse("用户 " + senderId);
    }

    private String conversationTitleOf(long userId, String conversationId) {
        Map<String, ConversationCard> cards = conversationApplicationService
                .listCards(userId).stream()
                .collect(Collectors.toMap(ConversationCard::conversationId, card -> card));
        ConversationCard card = cards.get(conversationId);
        return card == null ? conversationId : card.title();
    }
}
