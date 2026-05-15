package com.rag.controller;

import com.rag.entity.KbFile;
import com.rag.service.FileService;
import com.rag.service.KnowledgeBaseService;
import com.rag.vo.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件控制器
 * 提供文件上传、查询、删除和重新处理接口
 * 所有操作都校验用户对知识库的访问权限
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 上传文件到指定知识库
     * 后台异步执行解析、分块、向量化
     */
    @PostMapping("/upload")
    public Result<KbFile> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam("kbId") Long kbId,
                                 HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        knowledgeBaseService.checkAccess(kbId, userId);
        KbFile kbFile = fileService.upload(kbId, file);
        return Result.success(kbFile);
    }

    /** 查询知识库下的所有文件列表 */
    @GetMapping("/list/{kbId}")
    public Result<List<KbFile>> list(@PathVariable Long kbId, HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("userId");
        knowledgeBaseService.checkAccess(kbId, userId);
        List<KbFile> files = fileService.getByKbId(kbId);
        return Result.success(files);
    }

    /** 根据文件ID查询文件详情 */
    @GetMapping("/{id}")
    public Result<KbFile> getById(@PathVariable Long id) {
        KbFile file = fileService.getById(id);
        return Result.success(file);
    }

    /** 重新处理文件：清除旧切片和向量，重新解析和向量化 */
    @PostMapping("/{id}/reprocess")
    public Result<Void> reprocess(@PathVariable Long id) {
        fileService.reprocess(id);
        return Result.success();
    }

    /** 删除文件及其切片和向量 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return Result.success();
    }
}
