package com.rag.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识库文件实体类，对应数据库 kb_file 表
 * 记录用户上传到知识库中的文件信息及处理状态
 */
@Data
public class KbFile {
    /** 文件ID，主键 */
    private Long id;
    /** 所属知识库ID，关联 knowledge_base 表 */
    private Long kbId;
    /** 文件名 */
    private String fileName;
    /** 文件类型，如 pdf、txt、docx 等 */
    private String fileType;
    /** 文件大小（字节） */
    private Long fileSize;
    /** 文件存储路径 */
    private String filePath;
    /** 文件处理状态：pending-待处理，processing-处理中，completed-已完成，failed-处理失败 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
