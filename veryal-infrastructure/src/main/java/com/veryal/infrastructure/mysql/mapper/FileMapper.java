package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.FilePO;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件元数据 Mapper。统计类查询的 SQL 写在 {@code resources/mapper/FileMapper.xml}。
 */
public interface FileMapper extends BaseMapper<FilePO> {

    /** 已落盘文件占用的总字节数。 */
    long selectTotalBytes();

    /** 超过保留时间仍无任何业务引用的文件，孤儿清理用。 */
    List<FilePO> selectOrphans(@Param("threshold") LocalDateTime threshold,
                               @Param("limit") int limit);
}
