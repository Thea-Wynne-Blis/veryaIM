package com.veryal.infrastructure.mysql.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.veryal.infrastructure.mysql.po.OutboxPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox Mapper。
 *
 * <p>状态：0 待发布、1 已发布、2 等待重试、3 失败、4 发布中（本实现扩展，用于多实例抢占）。</p>
 */
public interface OutboxMapper extends BaseMapper<OutboxPO> {

    /**
     * 领取待发布记录：0 首次发布，2 退避到期后的重试。
     *
     * <p>重试以前只查 status = 0，而 {@link #markRetry} / {@link #recoverStale} 写的是 2，
     * 退避记录永远选不出来，发布失败一次就永久丢失。</p>
     */
    @Select("""
            SELECT id
            FROM im_outbox
            WHERE status IN (0, 2)
              AND next_retry_at <= #{now}
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<Long> selectPendingIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            UPDATE im_outbox
            SET status = 4, updated_at = #{now}
            WHERE id = #{id} AND status IN (0, 2)
            """)
    int claim(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE im_outbox
            SET status = 1, published_at = #{now}, updated_at = #{now}, last_error = NULL
            WHERE id = #{id}
            """)
    int markPublished(@Param("id") long id, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE im_outbox
            SET status = 2,
                retry_count = retry_count + 1,
                next_retry_at = #{nextRetryAt},
                updated_at = #{now},
                last_error = #{lastError}
            WHERE id = #{id}
            """)
    int markRetry(@Param("id") long id, @Param("nextRetryAt") LocalDateTime nextRetryAt,
                  @Param("now") LocalDateTime now, @Param("lastError") String lastError);

    @Update("""
            UPDATE im_outbox
            SET status = 3,
                retry_count = retry_count + 1,
                updated_at = #{now},
                last_error = #{lastError}
            WHERE id = #{id}
            """)
    int markFailed(@Param("id") long id, @Param("now") LocalDateTime now,
                   @Param("lastError") String lastError);

    @Update("""
            UPDATE im_outbox
            SET status = 2, next_retry_at = #{now}, updated_at = #{now},
                last_error = 'recovered from stale publishing state'
            WHERE status = 4 AND updated_at < #{staleBefore}
            """)
    int recoverStale(@Param("now") LocalDateTime now, @Param("staleBefore") LocalDateTime staleBefore);
}
