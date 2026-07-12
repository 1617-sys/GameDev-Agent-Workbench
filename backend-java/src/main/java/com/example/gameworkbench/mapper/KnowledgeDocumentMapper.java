package com.example.gameworkbench.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.gameworkbench.entity.KnowledgeDocument;

public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
    @Select("select * from knowledge_document where document_uuid = #{documentUuid} and project_id = #{projectId} and deleted = 0")
    KnowledgeDocument selectActiveByUuidAndProject(@Param("projectId") Long projectId, @Param("documentUuid") String documentUuid);

    @Select("select * from knowledge_document where project_id = #{projectId} and content_hash = #{contentHash} and deleted = 0")
    KnowledgeDocument selectActiveByHashAndProject(@Param("projectId") Long projectId, @Param("contentHash") String contentHash);

    @Select("select * from knowledge_document where project_id = #{projectId} and deleted = 0 order by version desc")
    List<KnowledgeDocument> selectActiveByProject(@Param("projectId") Long projectId);

    @Select("select coalesce(max(version), 0) from knowledge_document where project_id = #{projectId} for update")
    int selectLatestVersionForUpdate(@Param("projectId") Long projectId);
}
