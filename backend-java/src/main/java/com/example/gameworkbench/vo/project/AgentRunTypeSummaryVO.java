package com.example.gameworkbench.vo.project;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRunTypeSummaryVO {
    private String agentType;

    private Long totalCount;

    private Long successCount;

    private Long failedCount;

    private Double avgTimeTakenMs;
}
