package com.rag.mapper;

import com.rag.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 答案反馈 Mapper 接口
 */
@Mapper
public interface FeedbackMapper {
    /** 插入一条反馈记录 */
    int insert(Feedback feedback);
}
