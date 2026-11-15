package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * im_upload_session 映射对象。
 */
@Getter
@TableName("im_upload_session")
public class UploadSessionPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public long uploaderId;
    public String filename;
    public String contentType;
    public long sizeBytes;
    public int chunkSize;
    public int totalChunks;
    public String conversationId;
    public int status;
    public LocalDateTime createdAt;
    public Long fileId;
}
