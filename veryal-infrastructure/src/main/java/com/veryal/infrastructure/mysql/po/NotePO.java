package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 笔记表行。正文不在库里，object_key 指向文件存储中的 Markdown 文件。
 */
@Data
@TableName("im_note")
public class NotePO {

    @TableId
    public Long id;
    public Long userId;
    public String title;
    public String objectKey;
    public Long sizeBytes;
    public String excerpt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}
