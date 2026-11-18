package com.veryal.access.http.dto;

import com.veryal.application.search.ConversationSearchHit;
import com.veryal.common.core.CursorPage;
import java.util.List;

public record ConversationSearchResponse(List<ConversationSearchHit> messages,
                                          String nextCursor, boolean hasMore) {
    public static ConversationSearchResponse from(CursorPage<ConversationSearchHit> page) {
        return new ConversationSearchResponse(page.items(), page.nextCursor(), page.hasMore());
    }
}
