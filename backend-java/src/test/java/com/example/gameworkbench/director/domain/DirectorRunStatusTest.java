package com.example.gameworkbench.director.domain;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DirectorRunStatusTest {
    @Test void onlyAllowsFrozenTransitionsAndTerminalStatesNeverResume(){
        assertThat(DirectorRunStatus.PENDING.canTransitionTo(DirectorRunStatus.RUNNING)).isTrue();
        assertThat(DirectorRunStatus.RUNNING.canTransitionTo(DirectorRunStatus.WAITING_APPROVAL)).isTrue();
        assertThat(DirectorRunStatus.WAITING_APPROVAL.canTransitionTo(DirectorRunStatus.RUNNING)).isTrue();
        assertThat(DirectorRunStatus.SUCCEEDED.terminal()).isTrue();
        assertThat(DirectorRunStatus.SUCCEEDED.canTransitionTo(DirectorRunStatus.RUNNING)).isFalse();
    }
}
