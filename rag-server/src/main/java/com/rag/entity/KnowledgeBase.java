package com.rag.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库实体类，对应数据库 kb_knowledge_base 表
 * 表示用户创建的知识库，用于组织和管理上传的文档文件
 */
@Data
public class KnowledgeBase {
    /** 知识库ID，主键 */
    private Long id;
    /** 知识库名称 */
    private String name;
    /** 知识库描述 */
    private String description;
    /** 创建者用户ID，关联 sys_user 表 */
    private Long createUser;
    /** 可见范围：PRIVATE=私有, PUBLIC=公开 */
    private String visibility;
    /** 创建时间 */
    private LocalDateTime createTime;
}
