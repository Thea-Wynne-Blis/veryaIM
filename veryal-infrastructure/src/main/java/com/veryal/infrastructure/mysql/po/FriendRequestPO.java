package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_friend_request 映射对象。
 */
@Getter
@TableName("im_friend_request")
public class FriendRequestPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long fromUserId;
    public long toUserId;
    public String message;
    public int status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
