package com.example.gameworkbench.mapper;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRunEventMapperTest {
    @Test
    void sequenceAllocationMustBeDatabaseAtomicAndReplayMustBeOrdered() throws Exception {
        Update allocate = WorkflowRunEventMapper.class.getMethod("allocateNextSequence", String.class).getAnnotation(Update.class);
        Select replay = WorkflowRunEventMapper.class.getMethod("selectAfterSequence", String.class, long.class).getAnnotation(Select.class);

        assertThat(allocate.value()[0]).contains("event_sequence = event_sequence + 1");
        assertThat(replay.value()[0]).contains("sequence > #{afterSequence}").contains("order by sequence asc");
    }
}
