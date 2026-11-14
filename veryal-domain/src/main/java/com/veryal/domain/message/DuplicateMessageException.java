package com.veryal.domain.message;

import com.veryal.common.exception.BusinessException;
import com.veryal.common.exception.ErrorCode;

/**
 * 幂等键冲突：同一发送者使用相同 clientMsgId 重复提交时触发。
 */
public class DuplicateMessageException extends BusinessException {

    public DuplicateMessageException(long tenantId, long senderId, String clientMsgId) {
        super(ErrorCode.DUPLICATE_MESSAGE,
                "duplicate message: tenant=" + tenantId + ", sender=" + senderId + ", clientMsgId=" + clientMsgId);
    }
}
