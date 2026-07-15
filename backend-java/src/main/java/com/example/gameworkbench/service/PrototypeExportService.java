package com.example.gameworkbench.service;
import com.example.gameworkbench.entity.PrototypeExportJob;
import com.example.gameworkbench.vo.export.PrototypeExportJobVO;
public interface PrototypeExportService {
 PrototypeExportJobVO create(Long userId,String projectUuid,String versionUuid,String idempotencyKey);
 PrototypeExportJobVO get(Long userId,String projectUuid,String jobUuid);
 PrototypeExportJobVO retry(Long userId,String projectUuid,String jobUuid);
 PrototypeExportJob download(Long userId,String projectUuid,String jobUuid);
}
