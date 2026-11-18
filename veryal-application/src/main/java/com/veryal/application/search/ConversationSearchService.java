package com.veryal.application.search;

import com.veryal.application.message.MessageApplicationService;
import com.veryal.common.core.CursorPage;
import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;
import com.veryal.common.json.JsonUtils;
import com.veryal.domain.conversation.ConversationRepository;
import com.veryal.domain.file.FileRepository;
import com.veryal.domain.file.StoredFile;
import com.veryal.domain.friend.Friend;
import com.veryal.domain.friend.FriendRepository;
import com.veryal.domain.identity.User;
import com.veryal.domain.identity.UserRepository;
import com.veryal.domain.message.Message;
import com.veryal.domain.message.MessageRepository;
import com.veryal.domain.message.MessageSearchFilter;
import com.veryal.domain.message.MessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 统一搜索聊天记录和附件；游标分页不受最近一页消息或附件列表上限影响。 */
@Service
@RequiredArgsConstructor
public class ConversationSearchService {
    private final MessageApplicationService messageApplicationService;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;

    @Transactional(readOnly = true)
    public CursorPage<ConversationSearchHit> search(long userId, String conversationId,
            String keyword, String category, Long senderId, Instant from, Instant to,
            long cursor, int limit) {
        messageApplicationService.requireMember(conversationId, userId);
        String query = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (query.length() > 64 || (from != null && to != null && !from.isBefore(to))) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid search conditions");
        }
        List<Integer> types = switch (category) {
            case "ALL" -> List.of(1, 2, 3, 4, 5, 6);
            case "TEXT", "LINK" -> List.of(1, 6);
            case "FILE" -> List.of(3);
            case "MEDIA" -> List.of(2, 5);
            case "AUDIO" -> List.of(4);
            default -> throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "invalid search category");
        };
        String pattern = query.isEmpty() ? null : "%" + query.replace("!", "!!")
                .replace("%", "!%").replace("_", "!_") + "%";
        MessageSearchFilter filter = new MessageSearchFilter(types, pattern, senderId,
                from, to, "LINK".equals(category));
        long clearedSeq = conversationRepository.findMemberPrefs(conversationId, userId)
                .map(prefs -> prefs.clearedSeq()).orElse(0L);
        long before = cursor <= 0 ? Long.MAX_VALUE : cursor;
        int pageSize = Math.max(1, Math.min(limit, 100));
        List<ConversationSearchHit> hits = new ArrayList<>();
        Map<Long, String> remarks = friendRepository.listByUser(userId, 500).stream()
                .filter(friend -> friend.remark() != null && !friend.remark().isBlank())
                .collect(Collectors.toMap(Friend::friendId, Friend::remark, (a, b) -> a));

        while (hits.size() <= pageSize) {
            List<Message> candidates = messageRepository.searchCandidates(conversationId,
                    clearedSeq, before, filter, 200);
            if (candidates.isEmpty()) break;
            List<Long> fileIds = candidates.stream().map(ConversationSearchService::fileId)
                    .filter(Objects::nonNull).distinct().toList();
            Map<Long, StoredFile> files = fileIds.isEmpty() ? Map.of()
                    : fileRepository.findByIds(fileIds).stream()
                            .collect(Collectors.toMap(StoredFile::id, file -> file));
            Map<Long, User> users = userRepository.findByIds(candidates.stream()
                    .map(Message::senderId).distinct().toList()).stream()
                    .collect(Collectors.toMap(User::id, user -> user));
            for (Message message : candidates) {
                Long id = fileId(message);
                StoredFile file = id == null ? null : files.get(id);
                boolean attachment = isAttachment(message);
                if (attachment && (file == null || (!query.isEmpty()
                        && !file.originalName().toLowerCase(Locale.ROOT).contains(query)))) continue;
                User sender = users.get(message.senderId());
                String name = message.senderId() == userId ? "我"
                        : remarks.getOrDefault(message.senderId(),
                                sender == null ? "用户 " + message.senderId() : sender.nickname());
                String text = attachment ? "" : message.type() == MessageType.EMOJI
                        ? "[表情]" : message.textContent();
                hits.add(new ConversationSearchHit(message.id(), message.seq(), message.senderId(),
                        name, message.type().name(), text, message.createdAt(), id,
                        file == null ? null : file.originalName(),
                        file == null ? null : file.sizeBytes(), file == null ? null : file.contentType()));
                if (hits.size() > pageSize) break;
            }
            if (candidates.size() < 200) break;
            before = candidates.get(candidates.size() - 1).seq();
        }
        boolean hasMore = hits.size() > pageSize;
        List<ConversationSearchHit> page = hasMore ? hits.subList(0, pageSize) : hits;
        String next = page.isEmpty() ? null : Long.toString(page.get(page.size() - 1).seq());
        return new CursorPage<>(page, next, hasMore);
    }

    private static boolean isAttachment(Message message) {
        return switch (message.type()) {
            case IMAGE, FILE, AUDIO, VIDEO -> true;
            default -> false;
        };
    }

    private static Long fileId(Message message) {
        if (!isAttachment(message)) return null;
        try {
            long id = JsonUtils.mapper().readTree(message.content()).path("fileId").asLong(0);
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }
}
