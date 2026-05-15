package com.rag.mapper;

import com.rag.entity.KbFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 文件 Mapper 接口
 * 提供文件的增删改查操作
 */
@Mapper
public interface FileMapper {

    /** 根据文件ID查询文件信息 */
    KbFile findById(@Param("id") Long id);

    /** 查询知识库下的所有文件（按创建时间倒序） */
    List<KbFile> findByKbId(@Param("kbId") Long kbId);

    /** 新增文件记录 */
    int insert(KbFile file);

    /** 更新文件处理状态（UPLOADED/PROCESSING/COMPLETED/FAILED） */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 删除文件记录 */
    int deleteById(@Param("id") Long id);
}
