package com.rag.controller;

import com.rag.entity.ModelProvider;
import com.rag.service.ModelProviderService;
import com.rag.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 模型供应商控制器
 * 提供大模型供应商的增删改查和激活切换接口
 */
@RestController
@RequestMapping("/api/model-provider")
public class ModelProviderController {

    @Autowired
    private ModelProviderService modelProviderService;

    /** 查询所有供应商列表 */
    @GetMapping("/list")
    public Result<List<ModelProvider>> list() {
        return Result.success(modelProviderService.listAll());
    }

    /** 获取当前激活的供应商 */
    @GetMapping("/active")
    public Result<ModelProvider> getActive() {
        return Result.success(modelProviderService.getActive());
    }

    /** 新增供应商 */
    @PostMapping
    public Result<ModelProvider> create(@RequestBody ModelProvider provider) {
        return Result.success(modelProviderService.create(provider));
    }

    /** 修改供应商信息 */
    @PutMapping("/{id}")
    public Result<ModelProvider> update(@PathVariable Long id, @RequestBody ModelProvider provider) {
        return Result.success(modelProviderService.update(id, provider));
    }

    /** 删除供应商 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        modelProviderService.delete(id);
        return Result.success();
    }

    /** 激活指定供应商（取消其他供应商的激活状态） */
    @PutMapping("/{id}/activate")
    public Result<Void> activate(@PathVariable Long id) {
        modelProviderService.activate(id);
        return Result.success();
    }
}
