package com.rag.service;

import com.rag.ai.EmbeddingService;
import com.rag.common.BusinessException;
import com.rag.entity.KbChunk;
import com.rag.entity.KbFile;
import com.rag.mapper.ChunkMapper;
import com.rag.mapper.FileMapper;
import com.rag.rag.DocumentParser;
import com.rag.rag.QdrantService;
import com.rag.rag.TextSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * 文件服务
 * 负责文件的上传、解析、切片、向量化和删除
 * 文件处理在后台线程异步执行，不阻塞前端请求
 */
@Slf4j
@Service
public class FileService {

    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private ChunkMapper chunkMapper;
    @Autowired
    private DocumentParser documentParser;
    @Autowired
    private TextSplitter textSplitter;
    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private QdrantService qdrantService;
    @Autowired
    @Qualifier("fileProcessExecutor")
    private Executor fileProcessExecutor;

    @Value("${file.upload-dir}")
    private String uploadDir;
    @Value("${qdrant.upsert-batch-size:100}")
    private int qdrantUpsertBatchSize;

    private static final Set<String> allowedTypes = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "text/markdown",
            "text/x-markdown"
    );

    /**
     * 上传文件并创建后台处理任务
     * 保存文件到磁盘，写入数据库记录，然后异步执行解析和向量化
     */
    @Transactional
    public KbFile upload(Long kbId, MultipartFile file) {
        String contentType = file.getContentType();
        if (!allowedTypes.contains(contentType)) {
            throw new BusinessException(400, "Unsupported file type. Allowed: PDF, DOCX, TXT");
        }

        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path uploadPath = Paths.get(uploadDir);
        try {
            //创建目录
            Files.createDirectories(uploadPath);
            //文件名拼接到目录路径
            Path filePath = uploadPath.resolve(fileName);
            //上传的文件保存到磁盘上的指定位置
            file.transferTo(filePath.toFile());

            KbFile kbFile = new KbFile();
            kbFile.setKbId(kbId);
            kbFile.setFileName(file.getOriginalFilename());
            kbFile.setFileType(contentType != null ? contentType.split(";")[0] : "unknown");
            kbFile.setFileSize(file.getSize());
            kbFile.setFilePath(filePath.toString());
            kbFile.setStatus("UPLOADED");
            fileMapper.insert(kbFile);
            //提交后台异步任务
            submitProcessTask(kbFile);
            return kbFile;
        } catch (IOException e) {
            throw new BusinessException("Failed to save file: " + e.getMessage());
        }
    }

    /**
     * 提交后台处理任务，事务提交后才执行
     */
    private void submitProcessTask(KbFile kbFile) {
        //todo 如果当前有数据库事务在运行，就等事务成功提交后，再异步处理这个文件；如果没事务，就立刻处理
        //检查当前是否在 Spring 事务中
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            //注册一个事务同步回调（监听事务的生命周期）
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    //重写 afterCommit 方法：事务成功提交后自动调用
                    fileProcessExecutor.execute(() -> processFile(kbFile));
                }
            });
            return;
        }
        fileProcessExecutor.execute(() -> processFile(kbFile));
    }

    /**
     * 解析文件并写入文本切片和向量库
     */
    private void processFile(KbFile kbFile) {
        try {
            fileMapper.updateStatus(kbFile.getId(), "PROCESSING");
            File file = new File(kbFile.getFilePath());
            //提取文件内容为字符串
            String content = documentParser.parse(file);
            log.info("Parsed document, content length: {}", content.length());
            //按自适应策略分割文本
            List<String> chunkTexts = textSplitter.split(content);
            log.info("Split into {} chunks", chunkTexts.size());
            //存储文件拆分后的文本片段
            List<KbChunk> chunkBuffer = buildChunkBuffer(kbFile, chunkTexts);
            saveChunksBatch(chunkBuffer);
            List<KbChunk> savedChunks = chunkMapper.findByFileId(kbFile.getId());
            //批量插入向量库
            saveVectorsBatch(kbFile, savedChunks);
            fileMapper.updateStatus(kbFile.getId(), "COMPLETED");
            log.info("File processed successfully: {}", kbFile.getFileName());
        } catch (Exception e) {
            log.error("Failed to process file", e);
            fileMapper.updateStatus(kbFile.getId(), "FAILED");
        }
    }

    private List<KbChunk> buildChunkBuffer(KbFile kbFile, List<String> chunkTexts) {
        List<KbChunk> chunkBuffer = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            KbChunk chunk = new KbChunk();
            chunk.setFileId(kbFile.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunkTexts.get(i));
            // 记录来源信息：文件名 + 段落序号（用于引用溯源）
            chunk.setSourceInfo(kbFile.getFileName() + ", 第" + (i + 1) + "段");
            chunkBuffer.add(chunk);
        }
        return chunkBuffer;
    }

    private void saveChunksBatch(List<KbChunk> chunkBuffer) {
        if (chunkBuffer.isEmpty()) {
            return;
        }
        chunkMapper.insertBatch(chunkBuffer);
    }

    private void saveVectorsBatch(KbFile kbFile, List<KbChunk> savedChunks) {
        List<QdrantService.VectorPoint> vectorPoints = new ArrayList<>();
        for (KbChunk chunk : savedChunks) {
            //向量化
            addVectorPoint(vectorPoints, kbFile, chunk);
            //判断是否达到批量阈值，达到就批量插入向量库
            flushVectorPointsIfNeeded(vectorPoints);
        }
        flushVectorPoints(vectorPoints);
    }

    private void addVectorPoint(List<QdrantService.VectorPoint> vectorPoints, KbFile kbFile, KbChunk chunk) {
        try {
            String chunkText = chunk.getContent();
            float[] embedding = embeddingService.embed(chunkText);
            Map<String, Object> payload = new HashMap<>();
            payload.put("kb_id", kbFile.getKbId());
            payload.put("file_id", kbFile.getId());
            payload.put("chunk_id", chunk.getId());
            payload.put("content", chunkText);
            vectorPoints.add(new QdrantService.VectorPoint(chunk.getId(), embedding, payload));
        } catch (Exception e) {
            log.warn("Embedding failed for chunk {}, skipping vector storage: {}", chunk.getChunkIndex(), e.getMessage());
        }
    }

    private void flushVectorPointsIfNeeded(List<QdrantService.VectorPoint> vectorPoints) {
        // 检查条件：当前集合中的点数 < 批量阈值
        if (vectorPoints.size() < qdrantUpsertBatchSize) {
            return;// 未达标，什么都不做，直接返回
        }
        // 达标了，执行批量写入
        flushVectorPoints(vectorPoints);
    }

    private void flushVectorPoints(List<QdrantService.VectorPoint> vectorPoints) {
        if (vectorPoints.isEmpty()) {
            return;
        }
        try {
            List<QdrantService.VectorPoint> currentBatch = new ArrayList<>(vectorPoints);
            qdrantService.upsertBatch(currentBatch);
            vectorPoints.clear();
        } catch (Exception e) {
            log.warn("Batch upsert to Qdrant failed, size={}, skipping vector storage: {}", vectorPoints.size(), e.getMessage());
            vectorPoints.clear();
        }
    }

    /**
     * 查询知识库下的所有文件
     */
    public List<KbFile> getByKbId(Long kbId) {
        return fileMapper.findByKbId(kbId);
    }

    /**
     * 根据 ID 查询文件
     */
    public KbFile getById(Long id) {
        KbFile file = fileMapper.findById(id);
        if (file == null) {
            throw new BusinessException(404, "File not found");
        }
        return file;
    }

    /**
     * 重新处理文件
     * 清除旧的切片和向量后，重新解析、切片、向量化
     * 用于文件内容更新或处理失败后的重试
     */
    public void reprocess(Long id) {
        KbFile file = fileMapper.findById(id);
        if (file == null) {
            throw new BusinessException(404, "File not found");
        }
        // 清除旧的向量和切片
        qdrantService.deleteByFileId(id);
        chunkMapper.deleteByFileId(id);
        // 重新走处理流程
        fileMapper.updateStatus(id, "UPLOADED");
        submitProcessTask(file);
    }

    /**
     * 删除文件及其切片和向量
     */
    @Transactional
    public void delete(Long id) {
        KbFile file = fileMapper.findById(id);
        if (file == null) {
            throw new BusinessException(404, "File not found");
        }
        qdrantService.deleteByFileId(id);
        chunkMapper.deleteByFileId(id);
        try {
            Files.deleteIfExists(Paths.get(file.getFilePath()));
        } catch (IOException e) {
            log.error("Failed to delete file from disk", e);
        }
        fileMapper.deleteById(id);
    }
}
