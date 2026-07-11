package com.example.gameworkbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.OutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Select("""
            select * from outbox_event
            where status in ('PENDING', 'RETRY_PENDING')
              and (next_attempt_at is null or next_attempt_at <= #{now})
            order by id asc limit #{limit}
            """)
    List<OutboxEvent> selectDueForPublish(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            update outbox_event
            set status = 'PUBLISHING', claim_owner = #{owner}, claim_until = #{claimUntil},
                publish_attempt = publish_attempt + 1, updated_at = #{now}
            where id = #{id} and status in ('PENDING', 'RETRY_PENDING')
              and (next_attempt_at is null or next_attempt_at <= #{now})
            """)
    int claimForPublish(@Param("id") Long id, @Param("owner") String owner,
                        @Param("claimUntil") LocalDateTime claimUntil, @Param("now") LocalDateTime now);

    @Update("""
            update outbox_event
            set status = 'PUBLISHED', message_id = #{messageId}, published_at = #{now}, confirmed_at = #{now},
                claim_owner = null, claim_until = null, last_error_code = null, last_error_message = null, updated_at = #{now}
            where id = #{id} and status = 'PUBLISHING' and claim_owner = #{owner}
            """)
    int markPublished(@Param("id") Long id, @Param("owner") String owner,
                      @Param("messageId") String messageId, @Param("now") LocalDateTime now);

    @Update("""
            update outbox_event
            set status = 'RETRY_PENDING', claim_owner = null, claim_until = null, message_id = #{messageId},
                next_attempt_at = #{nextAttemptAt}, last_error_code = #{errorCode}, last_error_message = #{errorMessage}, updated_at = #{now}
            where id = #{id} and status in ('PUBLISHING', 'PUBLISHED')
            """)
    int markRetryableFailure(@Param("id") Long id, @Param("messageId") String messageId,
                             @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage,
                             @Param("nextAttemptAt") LocalDateTime nextAttemptAt, @Param("now") LocalDateTime now);

    @Update("""
            update outbox_event
            set status = 'RETRY_PENDING', claim_owner = null, claim_until = null, next_attempt_at = #{nextAttemptAt},
                last_error_code = 'PUBLISH_CONFIRM_TIMEOUT', last_error_message = 'Publisher confirm was not received before lease expiry', updated_at = #{now}
            where status = 'PUBLISHING' and claim_until < #{now}
            """)
    int recoverExpiredPublishingClaims(@Param("now") LocalDateTime now,
                                       @Param("nextAttemptAt") LocalDateTime nextAttemptAt);
}
