package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.FilePO;

/**
 * 文件元数据 Mapper。统计类查询的 SQL 写在 {@code resources/mapper/FileMapper.xml}。
 */
public interface FileMapper extends BaseMapper<FilePO> {

    /** 已落盘文件占用的总字节数。 */
    long selectTotalBytes();
}
