package com.example.gameworkbench.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.WorkflowRun;
import com.example.gameworkbench.entity.WorkflowRunEvent;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface WorkflowRunEventMapper extends BaseMapper<WorkflowRunEvent> {

    @Select("select * from workflow_run where workflow_run_uuid = #{workflowRunUuid} and deleted = 0 for update")
    WorkflowRun lockRunForEventAppend(@Param("workflowRunUuid") String workflowRunUuid);

    @Select("select * from workflow_run_event where workflow_run_uuid = #{workflowRunUuid} and event_key = #{eventKey}")
    WorkflowRunEvent selectByRunAndEventKey(@Param("workflowRunUuid") String workflowRunUuid, @Param("eventKey") String eventKey);

    @Update("update workflow_run set event_sequence = event_sequence + 1 where workflow_run_uuid = #{workflowRunUuid} and deleted = 0")
    int allocateNextSequence(@Param("workflowRunUuid") String workflowRunUuid);

    @Select("select event_sequence from workflow_run where workflow_run_uuid = #{workflowRunUuid} and deleted = 0")
    Long selectCurrentSequence(@Param("workflowRunUuid") String workflowRunUuid);

    @Select("""
            select * from workflow_run_event
            where workflow_run_uuid = #{workflowRunUuid} and sequence > #{afterSequence}
            order by sequence asc
            """)
    List<WorkflowRunEvent> selectAfterSequence(@Param("workflowRunUuid") String workflowRunUuid,
                                                @Param("afterSequence") long afterSequence);
}
