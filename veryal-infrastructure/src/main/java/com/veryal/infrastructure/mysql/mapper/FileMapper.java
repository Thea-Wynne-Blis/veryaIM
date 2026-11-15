package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.FilePO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 文件元数据 Mapper。
 */
public interface FileMapper extends BaseMapper<FilePO> {

    @Select("""
            SELECT COALESCE(SUM(size_bytes), 0)
            FROM im_file
            WHERE tenant_id = #{tenantId}
              AND status = 0
            """)
    long selectTotalBytes(@Param("tenantId") long tenantId);
}
