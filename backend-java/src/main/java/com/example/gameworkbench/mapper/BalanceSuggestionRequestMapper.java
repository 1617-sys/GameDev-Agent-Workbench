package com.example.gameworkbench.mapper;
import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.BalanceSuggestionRequest;
public interface BalanceSuggestionRequestMapper extends BaseMapper<BalanceSuggestionRequest> {
 @Select("select * from balance_suggestion_request where user_id=#{userId} and project_id=#{projectId} and idempotency_key=#{key} limit 1")
 BalanceSuggestionRequest selectIdempotent(@Param("userId") Long userId,@Param("projectId") Long projectId,@Param("key") String key);
}
