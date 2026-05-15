package com.rag.mapper;

import com.rag.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper {

    /** 根据ID查询知识库 */
    KnowledgeBase findById(@Param("id") Long id);

    /** 查询用户可见的知识库：自己的全部 + 他人的公开的 */
    List<KnowledgeBase> findVisible(@Param("userId") Long userId);

    /** 新增知识库 */
    int insert(KnowledgeBase kb);

    /** 删除知识库 */
    int deleteById(@Param("id") Long id);
}
