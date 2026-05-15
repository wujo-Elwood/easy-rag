package com.rag.service;

import com.rag.common.BusinessException;
import com.rag.entity.KnowledgeBase;
import com.rag.mapper.KnowledgeBaseMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库服务
 * 提供知识库的增删改查和权限校验
 */
@Service
public class KnowledgeBaseService {

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    /** 创建知识库，默认私有 */
    public KnowledgeBase create(String name, String description, Long userId) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(name);
        kb.setDescription(description);
        kb.setCreateUser(userId);
        kb.setVisibility("PRIVATE");
        knowledgeBaseMapper.insert(kb);
        return kb;
    }

    /** 根据 ID 查询知识库 */
    public KnowledgeBase getById(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.findById(id);
        if (kb == null) {
            throw new BusinessException(404, "Knowledge base not found");
        }
        return kb;
    }

    /**
     * 查询用户可见的知识库
     * 自己的全部可见 + 他人的公开的可见
     */
    public List<KnowledgeBase> getVisible(Long userId) {
        return knowledgeBaseMapper.findVisible(userId);
    }

    /** 删除知识库（只有创建者可以删除） */
    public void delete(Long id, Long userId) {
        KnowledgeBase kb = knowledgeBaseMapper.findById(id);
        if (kb == null) {
            throw new BusinessException(404, "Knowledge base not found");
        }
        if (!kb.getCreateUser().equals(userId)) {
            throw new BusinessException(403, "No permission to delete this knowledge base");
        }
        knowledgeBaseMapper.deleteById(id);
    }

    /**
     * 校验用户是否有权访问指定知识库
     * 创建者可以访问自己的，其他人只能访问公开的
     */
    public void checkAccess(Long kbId, Long userId) {
        KnowledgeBase kb = getById(kbId);
        if (!kb.getCreateUser().equals(userId) && !"PUBLIC".equals(kb.getVisibility())) {
            throw new BusinessException(403, "No permission to access this knowledge base");
        }
    }
}
