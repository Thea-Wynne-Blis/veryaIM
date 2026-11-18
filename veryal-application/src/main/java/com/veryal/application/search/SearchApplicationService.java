package com.veryal.application.search;

import com.veryal.application.conversation.ConversationApplicationService;
import com.veryal.application.conversation.ConversationCard;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.conversation.ConversationType;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.file.StoredFile;
import com.veryal.domain.friend.Friend;
import com.veryal.domain.friend.FriendRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.message.MessageType;
import com.veryal.domain.presence.PresenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局搜索：微信式搜索面板的后端。
 *
 * <p>四类结果共用一次请求，避免输入时连打四个接口。消息搜索只命中文本 / 表情，
 * 附件按文件名匹配；会话标题与发送者姓名都按「好友备注优先」返回。</p>
 */
@Service
@RequiredArgsConstructor
public class SearchApplicationService {

    private static final int MAX_KEYWORD = 64;
    private static final int HIT_LIMIT = 15;

    private final MessageRepository messageRepository;
    private final FriendRepository friendRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final PresenceRepository presenceRepository;
    private final ConversationApplicationService conversationApplicationService;

    @Transactional(readOnly = true)
    public SearchView search(long userId, String keyword) {
        String query = keyword == null ? "" : keyword.trim();
        if (query.isEmpty() || query.length() > MAX_KEYWORD) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "search keyword must be 1-" + MAX_KEYWORD + " characters");
        }
        String lower = query.toLowerCase(java.util.Locale.ROOT);
        String pattern = "%" + lower.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";

        List<ConversationCard> cards = conversationApplicationService.listCards(userId);
        Map<String, String> titles = cards.stream().collect(Collectors.toMap(
                ConversationCard::conversationId, ConversationCard::title, (a, b) -> a));

        Map<Long, String> remarks = friendRepository.listByUser(userId, 500).stream()
                .filter(friend -> friend.remark() != null && !friend.remark().isBlank())
                .collect(Collectors.toMap(Friend::friendId, Friend::remark, (a, b) -> a));

        return new SearchView(
                contacts(userId, lower),
                groups(cards, lower),
                messages(userId, pattern, titles, remarks),
                files(userId, lower, titles, remarks));
    }

    private List<SearchView.ContactHit> contacts(long userId, String lower) {
        List<Friend> friends = friendRepository.listByUser(userId, 500);
        if (friends.isEmpty()) {
            return List.of();
        }
        List<Long> ids = friends.stream().map(Friend::friendId).toList();
        Map<Long, User> users = userRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(User::id, user -> user));
        Set<Long> online = presenceRepository.onlineUserIds(ids);
        return friends.stream()
                .filter(friend -> {
                    User user = users.get(friend.friendId());
                    String nickname = user == null ? "" : user.nickname();
                    String remark = friend.remark() == null ? "" : friend.remark();
                    return contains(nickname, lower) || contains(remark, lower);
                })
                .limit(HIT_LIMIT)
                .map(friend -> {
                    User user = users.get(friend.friendId());
                    String nickname = user == null ? "用户 " + friend.friendId() : user.nickname();
                    String remark = friend.remark();
                    return new SearchView.ContactHit(friend.friendId(),
                            remark == null || remark.isBlank() ? nickname : remark,
                            nickname, remark, user == null ? null : user.avatar(),
                            online.contains(friend.friendId()));
                })
                .toList();
    }

    private List<SearchView.GroupHit> groups(List<ConversationCard> cards, String lower) {
        return cards.stream()
                .filter(card -> card.type() == ConversationType.GROUP)
                .filter(card -> contains(card.title(), lower))
                .limit(HIT_LIMIT)
                .map(card -> new SearchView.GroupHit(card.conversationId(), card.title(),
                        card.memberCount()))
                .toList();
    }

    private List<SearchView.MessageHit> messages(long userId, String pattern,
                                                 Map<String, String> titles,
                                                 Map<Long, String> remarks) {
        List<Message> messages = messageRepository.searchAcrossConversations(
                userId, pattern, HIT_LIMIT);
        Map<Long, User> users = usersOf(messages);
        return messages.stream()
                .map(message -> new SearchView.MessageHit(message.id(), message.conversationId(),
                        titles.getOrDefault(message.conversationId(), message.conversationId()),
                        message.senderId(), displayName(message.senderId(), userId, users, remarks),
                        textOf(message), message.createdAt()))
                .toList();
    }

    private List<SearchView.FileHit> files(long userId, String lower,
                                           Map<String, String> titles,
                                           Map<Long, String> remarks) {
        List<SearchView.FileHit> hits = new ArrayList<>();
        java.time.Instant beforeTime = null;
        long beforeId = Long.MAX_VALUE;
        while (hits.size() < HIT_LIMIT) {
            List<Message> messages = messageRepository.listMediaAcrossConversations(
                    userId, beforeTime, beforeId, 200);
            if (messages.isEmpty()) break;
            Map<Long, Long> fileIdByMessage = new HashMap<>();
            for (Message message : messages) {
                long fileId = fileIdOf(message.content());
                if (fileId > 0) fileIdByMessage.put(message.id(), fileId);
            }
            Map<Long, StoredFile> files = new HashMap<>();
            if (!fileIdByMessage.isEmpty()) {
                fileRepository.findByIds(new ArrayList<>(fileIdByMessage.values()))
                        .forEach(file -> files.put(file.id(), file));
            }
            Map<Long, User> users = usersOf(messages);
            for (Message message : messages) {
                StoredFile file = files.get(fileIdByMessage.get(message.id()));
                if (file == null || !contains(file.originalName(), lower)) continue;
                hits.add(new SearchView.FileHit(file.id(), message.id(),
                        message.conversationId(),
                        titles.getOrDefault(message.conversationId(), message.conversationId()),
                        file.originalName(), message.type().name(), file.sizeBytes(),
                        file.contentType(), message.senderId(),
                        displayName(message.senderId(), userId, users, remarks), message.createdAt()));
                if (hits.size() == HIT_LIMIT) break;
            }
            if (messages.size() < 200) break;
            Message last = messages.get(messages.size() - 1);
            beforeTime = last.createdAt();
            beforeId = last.id();
        }
        return hits;
    }

    private Map<Long, User> usersOf(List<Message> messages) {
        List<Long> ids = messages.stream().map(Message::senderId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(User::id, user -> user));
    }

    private static String displayName(long senderId, long userId, Map<Long, User> users,
                                      Map<Long, String> remarks) {
        if (senderId == userId) {
            return "我";
        }
        String remark = remarks.get(senderId);
        if (remark != null) {
            return remark;
        }
        User user = users.get(senderId);
        return user == null ? "用户 " + senderId : user.nickname();
    }

    private static String textOf(Message message) {
        if (message.type() == MessageType.TEXT) {
            return message.textContent();
        }
        return "[表情]";
    }

    private static long fileIdOf(byte[] content) {
        try {
            return JsonUtils.mapper().readTree(content).path("fileId").asLong(0);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static boolean contains(String value, String lower) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT).contains(lower);
    }
}
