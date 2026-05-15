package com.rag.controller;

import com.rag.entity.KnowledgeBase;
import com.rag.service.KnowledgeBaseService;
import com.rag.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库控制器
 * 提供知识库的增删查和权限管理
 */
@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /** 创建知识库（默认私有） */
    @PostMapping
    public Result<KnowledgeBase> create(@RequestBody KnowledgeBase request, HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        KnowledgeBase kb = knowledgeBaseService.create(request.getName(), request.getDescription(), userId);
        return Result.success(kb);
    }

    /** 查询用户可见的知识库（自己的全部 + 他人公开的） */
    @GetMapping
    public Result<List<KnowledgeBase>> list(HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        List<KnowledgeBase> list = knowledgeBaseService.getVisible(userId);
        return Result.success(list);
    }

    /** 根据ID查询知识库详情 */
    @GetMapping("/{id}")
    public Result<KnowledgeBase> getById(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        return Result.success(kb);
    }

    /** 删除知识库（只有创建者可以删除） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        knowledgeBaseService.delete(id, userId);
        return Result.success();
    }
}
