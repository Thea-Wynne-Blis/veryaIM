package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_file 映射对象。
 */
@Getter
@TableName("im_file")
public class FilePO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long tenantId;
    public long uploaderId;
    public String objectKey;
    public String originalName;
    public String contentType;
    public long sizeBytes;
    public String sha256;
    public String storageType;
    public int status;
    public LocalDateTime createdAt;
    public LocalDateTime deletedAt;
    public String conversationId;
}
