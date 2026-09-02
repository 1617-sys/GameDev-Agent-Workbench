package com.example.gameworkbench.service;

import com.example.gameworkbench.dto.episode.PersistMachineEpisodeBatchRequest;
import com.example.gameworkbench.vo.episode.MachineEpisodeAggregateVO;
import com.example.gameworkbench.vo.episode.MachineEpisodeBatchVO;
import com.example.gameworkbench.vo.episode.MachineEpisodeVO;
import com.example.gameworkbench.vo.episode.MachineEpisodeStepPageVO;

/**
 * 自动试玩轨迹的应用服务边界。
 *
 * <p>Batch 表示一次批量评测，Episode 表示其中一局，Step 表示一局中的单步观察与动作。
 * 接口同时提供写入、明细查询和按原型版本聚合，Controller 不需要了解三层数据表的关系。</p>
 */
public interface MachineEpisodeService {
    /** 幂等保存 Python Player 返回的一整批 episode 与 step。 */
    MachineEpisodeBatchVO persistBatch(Long userId, String projectUuid, String idempotencyKey,
            PersistMachineEpisodeBatchRequest request);

    /** 查询批次元数据及处理结果。 */
    MachineEpisodeBatchVO getBatch(Long userId, String projectUuid, String batchUuid);

    /** 查询单局完整信息，不包含分页的 step 列表。 */
    MachineEpisodeVO getEpisode(Long userId, String projectUuid, String episodeUuid);

    /** 返回适合列表页展示的单局摘要。 */
    MachineEpisodeVO getEpisodeSummary(Long userId,String projectUuid,String episodeUuid);

    /** 汇总同一原型版本下多局自动试玩的指标。 */
    MachineEpisodeAggregateVO aggregate(Long userId, String projectUuid, String prototypeVersionUuid);

    /** 分页读取高数据量的逐步轨迹，避免一次加载完整 episode。 */
    MachineEpisodeStepPageVO getSteps(Long userId,String projectUuid,String episodeUuid,int page,int size);
}
