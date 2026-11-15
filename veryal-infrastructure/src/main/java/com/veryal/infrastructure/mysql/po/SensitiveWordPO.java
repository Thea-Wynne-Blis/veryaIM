package com.veryal.infrastructure.mysql.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * im_sensitive_word 映射对象。
 */
@Getter
@Setter
@TableName("im_sensitive_word")
public class SensitiveWordPO {

    @TableId(type = IdType.INPUT)
    public Long id;
    public String word;
    public int enabled;
    public LocalDateTime createdAt;
}
