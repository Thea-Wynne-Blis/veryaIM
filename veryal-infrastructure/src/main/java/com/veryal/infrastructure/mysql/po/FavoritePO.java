package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏表行。内容为原始消息 content 的快照。
 */
@Data
@TableName("im_favorite")
public class FavoritePO {

    @TableId
    public Long id;
    public Long userId;
    public String conversationId;
    public Long messageId;
    public Integer msgType;
    public byte[] content;
    public Long fileId;
    public String fileName;
    public Long sizeBytes;
    public String contentType;
    public Long senderId;
    public String senderName;
    public String conversationTitle;
    public LocalDateTime createdAt;
}
