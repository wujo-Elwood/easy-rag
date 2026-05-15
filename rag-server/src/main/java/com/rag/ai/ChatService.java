package com.rag.ai;

import com.rag.entity.ChatMessage;
import com.rag.entity.KbChunk;
import com.rag.entity.ModelProvider;
import com.rag.entity.UsageLog;
import com.rag.mapper.ChatMessageMapper;
import com.rag.mapper.ChunkMapper;
import com.rag.rag.*;
import com.rag.rag.QdrantService.SearchResult;
import com.rag.service.ModelProviderService;
import com.rag.service.UsageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 聊天服务
 * 核心职责：多轮对话管理、RAG 检索增强、大模型调用、用量记录
 *
 * 工作流程：
 * 1. 安全检查（Prompt 注入防护）
 * 2. 保存用户消息到数据库
 * 3. 查询改写 + 混合检索 + 重排序 + 缓存 → 构建知识库上下文（含来源信息）
 * 4. 加载历史对话消息（多轮上下文）
 * 5. 组装 system/user/assistant 消息数组发给大模型（含引用溯源指令）
 * 6. 保存助手回复，记录用量
 */
@Slf4j
@Service
public class ChatService {

    /** 从配置文件读取召回数量（默认 5） */
    @Value("${rag.top-k}")
    private int topK;

    /** 多轮对话：最多加载的历史消息条数 */
    private static final int MAX_HISTORY_MESSAGES = 10;

    private final EmbeddingService embeddingService;
    private final QdrantService qdrantService;
    private final ChunkMapper chunkMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final ModelProviderService modelProviderService;
    private final Reranker reranker;
    private final KeywordSearchService keywordSearchService;
    private final UsageService usageService;
    private final PromptGuard promptGuard;
    private final QueryRewriter queryRewriter;
    private final RetrievalCache retrievalCache;
    private final ContextCompressor contextCompressor;
    private final Executor chatStreamExecutor;

    /** 系统提示词：要求模型基于知识库上下文回答并标注来源 */
    private static final String SYSTEM_PROMPT = "你是一个智能 AI 助手。\n\n"
            + "如果提供了知识库上下文，请优先基于上下文回答问题。\n"
            + "回答时请在相关语句后用 [来源: 文件名, 第N段] 标注引用来源。\n"
            + "如果没有提供知识库上下文，请直接使用大模型自身知识回答。\n"
            + "回答要简洁、准确、有帮助。";

    /** 构造注入所有依赖的组件 */
    public ChatService(EmbeddingService embeddingService, QdrantService qdrantService,
                       ChunkMapper chunkMapper, ChatMessageMapper chatMessageMapper,
                       ModelProviderService modelProviderService,
                       Reranker reranker, KeywordSearchService keywordSearchService,
                       UsageService usageService,
                       PromptGuard promptGuard, QueryRewriter queryRewriter,
                       RetrievalCache retrievalCache, ContextCompressor contextCompressor,
                       @Qualifier("chatStreamExecutor") Executor chatStreamExecutor) {
        this.embeddingService = embeddingService;
        this.qdrantService = qdrantService;
        this.chunkMapper = chunkMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.modelProviderService = modelProviderService;
        this.reranker = reranker;
        this.keywordSearchService = keywordSearchService;
        this.usageService = usageService;
        this.promptGuard = promptGuard;
        this.queryRewriter = queryRewriter;
        this.retrievalCache = retrievalCache;
        this.contextCompressor = contextCompressor;
        this.chatStreamExecutor = chatStreamExecutor;
    }

    // ==================== 同步聊天 ====================

    /**
     * 同步聊天接口
     * 完整流程：安全检查 → 保存用户消息 → 检索知识库 → 构建多轮消息 → 调用大模型 → 保存回复 → 记录用量
     *
     * @param sessionId 会话ID
     * @param message   用户发送的消息内容
     * @param kbId      知识库ID（可选，为空则不检索知识库）
     * @return 大模型生成的回复文本
     */
    public String chat(Long sessionId, String message, Long kbId) {
        // 安全检查：拦截 Prompt 注入攻击
        promptGuard.check(message);
        // 获取当前激活的大模型供应商配置
        ModelProvider provider = modelProviderService.getActive();
        // 保存用户消息到数据库
        saveMessage(sessionId, "user", message);

        // 混合检索 + 重排序，构建带来源信息的知识库上下文
        ContextResult contextResult = buildContextWithSources(message, kbId);
        // 组装 system + 历史消息 + 当前问题的 Spring AI 消息列表
        List<Message> chatMessages = buildChatMessages(sessionId, message, contextResult);

        long startTime = System.currentTimeMillis();
        try {
            // 使用 Spring AI 同步调用大模型，等待完整回复
            LlmResult llmResult = callLlmSync(chatMessages, provider);
            // 保存助手回复到数据库
            saveMessage(sessionId, "assistant", llmResult.content);
            // 记录本次调用的 Token 用量和耗时
            recordUsage(sessionId, provider, startTime, "SUCCESS",
                    llmResult.promptTokens, llmResult.completionTokens);
            return llmResult.content;
        } catch (Exception e) {
            // 调用失败也记录用量（状态为 FAILED）
            recordUsage(sessionId, provider, startTime, "FAILED", 0, 0);
            throw e;
        }
    }

    // ==================== 流式聊天 ====================

    /**
     * 流式聊天接口
     * 保存用户消息后立即返回 SseEmitter，后台线程执行检索和流式推送
     * 前端通过 SSE 逐字接收大模型回复，实现打字机效果
     *
     * @param sessionId 会话ID
     * @param message   用户发送的消息内容
     * @param kbId      知识库ID（可选）
     * @return SSE 推送对象，前端通过此对象接收流式数据
     */
    public SseEmitter chatStream(Long sessionId, String message, Long kbId) {
        // 安全检查：拦截 Prompt 注入攻击
        promptGuard.check(message);
        // 获取当前激活的大模型供应商配置
        ModelProvider provider = modelProviderService.getActive();
        // 保存用户消息到数据库
        saveMessage(sessionId, "user", message);
        // 创建 SSE 推送对象（超时设为 0 表示不超时）
        SseEmitter emitter = new SseEmitter(0L);
        // 把检索和大模型调用放到后台线程执行，不阻塞主线程
        chatStreamExecutor.execute(() -> prepareAndStreamResponse(sessionId, message, kbId, emitter, provider));
        // 立即返回 SSE 对象给控制器
        return emitter;
    }

    /**
     * 准备上下文并开始流式推送（在后台线程中执行）
     * 流程：检索知识库 → 构建消息 → 流式调用大模型 → 推送 token 到前端
     *
     * @param sessionId 会话ID
     * @param message   用户消息
     * @param kbId      知识库ID
     * @param emitter   SSE 推送对象
     * @param provider  大模型供应商配置
     */
    private void prepareAndStreamResponse(Long sessionId, String message, Long kbId,
                                          SseEmitter emitter, ModelProvider provider) {
        long startTime = System.currentTimeMillis();
        try {
            // 通知前端正在检索知识库
            safeSend(emitter, SseEmitter.event().name("status").data("正在检索知识库并生成内容..."));
            // 混合检索 + 重排序，构建知识库上下文
            ContextResult contextResult = buildContextWithSources(message, kbId);
            // 组装 Spring AI 消息列表
            List<Message> chatMessages = buildChatMessages(sessionId, message, contextResult);
            // 通知前端正在生成
            safeSend(emitter, SseEmitter.event().name("status").data("正在生成..."));
            // 使用 Spring AI 流式调用大模型，逐段推送到前端
            LlmResult llmResult = streamLlmResponse(sessionId, chatMessages, emitter, provider);
            // 记录 Token 用量
            recordUsage(sessionId, provider, startTime, "SUCCESS",
                    llmResult.promptTokens, llmResult.completionTokens);
        } catch (Exception e) {
            log.error("Failed to prepare stream response", e);
            recordUsage(sessionId, provider, startTime, "FAILED", 0, 0);
            completeEmitterWithError(emitter, e);
        }
    }

    /**
     * 使用 Spring AI 流式调用大模型
     *
     * @param sessionId 会话ID
     * @param chatMessages 组装好的 Spring AI 消息列表
     * @param emitter   SSE 推送对象
     * @param provider  大模型供应商配置
     * @return LlmResult 包含完整回复内容和 Token 用量
     */
    private LlmResult streamLlmResponse(Long sessionId, List<Message> chatMessages,
                                        SseEmitter emitter, ModelProvider provider) {
        // 第1步：创建完整回复缓存
        StringBuilder fullResponse = new StringBuilder();
        // 第2步：创建 Token 用量缓存
        int[] tokenUsage = {0, 0};
        try {
            // 第3步：根据数据库供应商配置动态创建 Spring AI 模型
            OpenAiChatModel chatModel = createSpringAiChatModel(provider);
            // 第4步：把消息列表包装成 Spring AI Prompt
            Prompt prompt = new Prompt(chatMessages);
            // 第5步：通知前端连接已经建立
            safeSend(emitter, SseEmitter.event().name("open").data("connected"));
            // 第6步：订阅 Spring AI 流式响应并逐段推送给前端
            chatModel.stream(prompt).toIterable().forEach(response -> {
                parseUsage(response, tokenUsage);
                String text = extractResponseText(response);
                if (!text.isEmpty()) {
                    fullResponse.append(text);
                    safeSend(emitter, SseEmitter.event().name("message").data(text));
                }
            });
            // 第7步：把完整回复保存到数据库
            if (!fullResponse.isEmpty()) {
                saveMessage(sessionId, "assistant", fullResponse.toString());
            }
            // 第8步：通知前端流式输出完成
            safeSend(emitter, SseEmitter.event().name("done").data("[DONE]"));
            safeComplete(emitter);
        } catch (Exception e) {
            // 第9步：流式调用失败时记录日志
            log.error("Spring AI streaming error, provider={}, model={}", provider.getName(), provider.getModel(), e);
            // 第10步：如果已经生成了部分内容，就先保存部分内容
            if (!fullResponse.isEmpty()) {
                saveMessage(sessionId, "assistant", fullResponse.toString());
            }
            // 第11步：把异常继续抛给外层统一处理
            throw new RuntimeException("Failed to call LLM API by Spring AI: " + buildUserErrorMessage(e), e);
        }
        // 第12步：返回完整回复和用量
        return new LlmResult(fullResponse.toString(), tokenUsage[0], tokenUsage[1]);
    }

    /**
     * 使用 Spring AI 同步调用大模型
     *
     * @param chatMessages 组装好的 Spring AI 消息列表
     * @param provider     大模型供应商配置
     * @return LlmResult 包含回复内容和 Token 用量
     */
    private LlmResult callLlmSync(List<Message> chatMessages, ModelProvider provider) {
        try {
            // 第1步：根据数据库供应商配置动态创建 Spring AI 模型
            OpenAiChatModel chatModel = createSpringAiChatModel(provider);
            // 第2步：把消息列表包装成 Spring AI Prompt
            Prompt prompt = new Prompt(chatMessages);
            // 第3步：同步调用模型
            ChatResponse response = chatModel.call(prompt);
            // 第4步：提取回复正文
            String content = extractResponseText(response);
            // 第5步：提取 Token 用量
            int[] tokenUsage = {0, 0};
            parseUsage(response, tokenUsage);
            // 第6步：返回调用结果
            return new LlmResult(content, tokenUsage[0], tokenUsage[1]);
        } catch (Exception e) {
            // 第7步：调用失败时记录日志
            log.error("Spring AI call failed, provider={}, model={}", provider.getName(), provider.getModel(), e);
            // 第8步：抛出统一错误信息
            throw new RuntimeException("Failed to call LLM API by Spring AI: " + buildUserErrorMessage(e), e);
        }
    }

    // ==================== 多轮对话上下文 ====================

    /**
     * 构建发送给大模型的完整消息数组
     * 包含三部分：system 提示词（含知识库上下文）、历史对话消息、当前用户问题
     *
     * @param sessionId    会话ID（用于加载历史消息）
     * @param message      当前用户问题
     * @param contextResult 知识库检索结果（含来源标注）
     * @return 消息数组，按 system → history → user 排列
     */
    private List<Message> buildChatMessages(Long sessionId, String message, ContextResult contextResult) {
        List<Message> messages = new ArrayList<>();

        // 第1步：构建系统提示词
        String systemContent = SYSTEM_PROMPT;
        if (contextResult.hasContext()) {
            // 第2步：对检索到的上下文进行压缩，减少 token 消耗
            String compressedContext = contextCompressor.compress(message, contextResult.getContextWithSources());
            systemContent += "\n\n以下是知识库中的相关内容：\n\n" + compressedContext;
        }
        // 第3步：把系统提示词加入 Spring AI 消息列表
        messages.add(new SystemMessage(systemContent));

        // 第4步：加载最近的历史消息
        List<ChatMessage> history = chatMessageMapper.findBySessionId(sessionId);
        if (history.size() > 1) {
            // 第5步：去掉最后一条当前用户消息，避免重复发送
            List<ChatMessage> previousMessages = history.subList(0, history.size() - 1);
            // 第6步：只取最近若干条历史消息，避免上下文过长
            int start = Math.max(0, previousMessages.size() - MAX_HISTORY_MESSAGES);
            for (int i = start; i < previousMessages.size(); i++) {
                ChatMessage msg = previousMessages.get(i);
                // 第7步：按角色转换为 Spring AI 消息类型
                if ("assistant".equals(msg.getRole())) {
                    messages.add(new AssistantMessage(msg.getContent()));
                } else {
                    messages.add(new UserMessage(msg.getContent()));
                }
            }
        }

        // 第8步：加入当前用户消息
        messages.add(new UserMessage(message));

        return messages;
    }

    // ==================== 混合检索 + 查询改写 + 缓存 + 引用溯源 ====================

    /**
     * 构建知识库上下文（带来源信息）
     * 完整流程：查询改写 → 缓存检查 → 向量检索 + 关键词检索 → 合并去重 → 重排序 → 返回结构化结果
     *
     * @param message 用户原始问题
     * @param kbId    知识库ID（为空时不检索，返回空结果）
     * @return ContextResult 包含纯文本上下文和带来源标注的上下文
     */
    private ContextResult buildContextWithSources(String message, Long kbId) {
        if (kbId == null) {
            return ContextResult.empty();
        }
        try {
            // 第1步：查询改写（口语化问题 → 适合向量检索的独立查询）
            String rewrittenQuery = queryRewriter.rewrite(message);
            String searchQuery = rewrittenQuery.isEmpty() ? message : rewrittenQuery;

            // 第2步：检查缓存（相同问题 5 分钟内直接返回缓存结果）
            List<Long> cachedIds = retrievalCache.get(kbId, searchQuery);
            if (cachedIds != null && !cachedIds.isEmpty()) {
                List<KbChunk> cachedChunks = findKbChunks(cachedIds, kbId);
                if (!cachedChunks.isEmpty()) {
                    return buildContextResult(cachedChunks);
                }
            }

            // 第3步：向量检索（语义相似度匹配，多取一些用于后续重排序）
            float[] queryEmbedding = embeddingService.embed(searchQuery);
            List<SearchResult> vectorResults = qdrantService.searchWithScore(queryEmbedding, topK * 2, kbId);
            List<Long> vectorIds = vectorResults.stream().map(r -> r.id).collect(Collectors.toList());
            List<Float> vectorScores = vectorResults.stream().map(r -> r.score).collect(Collectors.toList());

            // 第4步：关键词检索（MySQL LIKE 精确匹配）
            List<KbChunk> keywordChunks = keywordSearchService.search(searchQuery, kbId, topK);

            // 第5步：合并去重（向量结果 + 关键词结果，去掉重复的 chunk）
            Set<Long> allIds = new LinkedHashSet<>(vectorIds);
            List<KbChunk> vectorChunks = findKbChunks(vectorIds, kbId);
            for (KbChunk chunk : keywordChunks) {
                if (allIds.add(chunk.getId())) {
                    // 关键词检索命中但向量检索没命中的，补充进来
                    vectorChunks.add(chunk);
                    vectorScores.add(0.3f); // 给关键词命中的一个基础分数
                }
            }

            // 兼容旧数据：如果带 kb_id 过滤没命中，去掉过滤再搜一次
            if (vectorChunks.isEmpty()) {
                List<Long> legacyIds = qdrantService.search(queryEmbedding, topK);
                vectorChunks = findKbChunks(legacyIds, kbId);
            }
            if (vectorChunks.isEmpty()) {
                return ContextResult.empty();
            }

            // 第6步：重排序（融合向量分数和关键词分数，选出最相关的 topK 个）
            List<KbChunk> reranked = reranker.rerank(searchQuery, vectorChunks, vectorScores, topK);

            // 第7步：写入缓存（下次相同问题直接命中）
            List<Long> rerankedIds = reranked.stream().map(KbChunk::getId).collect(Collectors.toList());
            retrievalCache.put(kbId, searchQuery, rerankedIds);

            return buildContextResult(reranked);
        } catch (Exception e) {
            log.warn("Failed to build context: {}", e.getMessage());
            return ContextResult.empty();
        }
    }

    /**
     * 从 chunk 列表构建带来源信息的上下文结果
     * 生成两个版本：纯文本版（用于缓存）和带来源标注版（用于 prompt）
     *
     * @param chunks 重排序后的 chunk 列表
     * @return ContextResult 包含两种格式的上下文文本
     */
    private ContextResult buildContextResult(List<KbChunk> chunks) {
        StringBuilder contextText = new StringBuilder();
        StringBuilder contextWithSources = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            KbChunk chunk = chunks.get(i);
            if (i > 0) {
                contextText.append("\n\n");
                contextWithSources.append("\n\n");
            }
            // 纯文本版本
            contextText.append(chunk.getContent());
            // 带来源标注的版本：[来源: 文件名, 第N段]
            String source = chunk.getSourceInfo() != null ? chunk.getSourceInfo() : "文档";
            contextWithSources.append("[来源: ").append(source).append("]\n").append(chunk.getContent());
        }
        return new ContextResult(contextText.toString(), contextWithSources.toString());
    }

    /**
     * 根据 chunk ID 列表从 MySQL 查询对应的文本块
     * 通过 findByIdAndKbId 确保 chunk 属于指定的知识库（权限隔离）
     *
     * @param chunkIds chunk ID 列表（来自 Qdrant 检索结果）
     * @param kbId     知识库ID（用于权限校验）
     * @return 查询到的 chunk 列表（过滤掉不存在或不属于该知识库的）
     */
    private List<KbChunk> findKbChunks(List<Long> chunkIds, Long kbId) {
        return chunkIds.stream()
                .map(chunkId -> chunkMapper.findByIdAndKbId(chunkId, kbId))
                .filter(chunk -> chunk != null)
                .collect(Collectors.toList());
    }

    /**
     * 上下文结果内部类
     * 包含纯文本版本和带来源标注的版本，供不同场景使用
     */
    static class ContextResult {
        /** 纯文本上下文（用于缓存） */
        final String context;
        /** 带来源标注的上下文（用于 system prompt，支持引用溯源） */
        final String contextWithSources;

        ContextResult(String context, String contextWithSources) {
            this.context = context;
            this.contextWithSources = contextWithSources;
        }

        /** 创建空的上下文结果（未选择知识库或检索失败时使用） */
        static ContextResult empty() { return new ContextResult("", ""); }

        /** 是否有上下文内容 */
        boolean hasContext() { return !context.isEmpty(); }

        /** 获取带来源标注的上下文文本 */
        String getContextWithSources() { return contextWithSources; }
    }

    /**
     * 召回测试接口（供 ChatController 调用）
     * 输入问题和知识库ID，返回召回的 chunk 列表，用于调试检索质量
     * 每个 chunk 包含排名、chunkID、来源信息和内容
     *
     * @param message 用户输入的测试问题
     * @param kbId    知识库ID
     * @return 召回结果列表，每项包含 rank、chunkId、source、content
     */
    public List<Map<String, Object>> recallTest(String message, Long kbId) {
        if (kbId == null) {
            return Collections.emptyList();
        }
        try {
            // 查询改写
            String rewrittenQuery = queryRewriter.rewrite(message);
            String searchQuery = rewrittenQuery.isEmpty() ? message : rewrittenQuery;

            // 向量检索
            float[] queryEmbedding = embeddingService.embed(searchQuery);
            List<SearchResult> vectorResults = qdrantService.searchWithScore(queryEmbedding, topK * 2, kbId);
            List<Long> vectorIds = vectorResults.stream().map(r -> r.id).collect(Collectors.toList());
            List<Float> vectorScores = vectorResults.stream().map(r -> r.score).collect(Collectors.toList());

            // 关键词检索
            List<KbChunk> keywordChunks = keywordSearchService.search(searchQuery, kbId, topK);

            // 合并去重
            Set<Long> allIds = new LinkedHashSet<>(vectorIds);
            List<KbChunk> vectorChunks = findKbChunks(vectorIds, kbId);
            for (KbChunk chunk : keywordChunks) {
                if (allIds.add(chunk.getId())) {
                    vectorChunks.add(chunk);
                    vectorScores.add(0.3f);
                }
            }

            // 重排序
            List<KbChunk> reranked = reranker.rerank(searchQuery, vectorChunks, vectorScores, topK);

            // 构建返回结果
            List<Map<String, Object>> results = new ArrayList<>();
            for (int i = 0; i < reranked.size(); i++) {
                KbChunk chunk = reranked.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("rank", i + 1);
                item.put("chunkId", chunk.getId());
                item.put("source", chunk.getSourceInfo() != null ? chunk.getSourceInfo() : "未知来源");
                item.put("content", chunk.getContent());
                results.add(item);
            }
            return results;
        } catch (Exception e) {
            log.warn("Recall test failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 用量记录 ====================

    /**
     * 记录一次 LLM 调用的用量信息
     * 包括供应商信息、Token 消耗、响应耗时和调用状态
     *
     * @param sessionId      会话ID
     * @param provider       大模型供应商配置
     * @param startTime      调用开始时间戳（毫秒）
     * @param status         调用状态：SUCCESS 或 FAILED
     * @param promptTokens   输入 Token 数
     * @param completionTokens 输出 Token 数
     */
    private void recordUsage(Long sessionId, ModelProvider provider, long startTime,
                             String status, int promptTokens, int completionTokens) {
        try {
            UsageLog logEntry = new UsageLog();
            logEntry.setSessionId(sessionId);
            logEntry.setProviderId(provider.getId());
            logEntry.setProviderName(provider.getName());
            logEntry.setModel(provider.getModel());
            logEntry.setPromptTokens(promptTokens);
            logEntry.setCompletionTokens(completionTokens);
            logEntry.setTotalTokens(promptTokens + completionTokens);
            logEntry.setDurationMs((int) (System.currentTimeMillis() - startTime));
            logEntry.setStatus(status);
            usageService.record(logEntry);
        } catch (Exception e) {
            log.error("Failed to record usage", e);
        }
    }

    /**
     * LLM 调用结果内部类
     * 封装大模型返回的回复内容和 Token 用量统计
     */
    private static class LlmResult {
        /** 模型生成的回复文本 */
        final String content;
        /** 输入 Token 数（prompt_tokens） */
        final int promptTokens;
        /** 输出 Token 数（completion_tokens） */
        final int completionTokens;

        LlmResult(String content, int promptTokens, int completionTokens) {
            this.content = content;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }
    }

    // ==================== Spring AI 调用辅助 ====================

    /**
     * 根据数据库中的供应商配置创建 Spring AI 聊天模型
     *
     * @param provider 大模型供应商配置
     * @return Spring AI OpenAiChatModel
     */
    private OpenAiChatModel createSpringAiChatModel(ModelProvider provider) {
        // 第1步：规范化 baseUrl，确保交给 Spring AI 的地址只到 /v1 之前或 /v1
        String baseUrl = normalizeSpringAiBaseUrl(provider.getBaseUrl());
        // 第2步：使用动态 API Key 和 baseUrl 创建 OpenAiApi
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(provider.getApiKey())
                .build();
        // 第3步：设置模型名、温度和流式 usage 返回
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .model(provider.getModel())
                .temperature(0.3)
                .streamUsage(true)
                .build();
        // 第4步：创建 Spring AI ChatModel
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(chatOptions)
                .build();
    }

    /**
     * 规范化 Spring AI 使用的 baseUrl
     *
     * @param rawBaseUrl 用户配置的 API 地址
     * @return 可以交给 Spring AI 的 baseUrl
     */
    private String normalizeSpringAiBaseUrl(String rawBaseUrl) {
        // 第1步：去掉末尾多余斜杠
        String baseUrl = rawBaseUrl.replaceAll("/+$", "");
        // 第2步：如果用户填了完整 chat/completions 地址，就截断到 /v1
        if (baseUrl.endsWith("/chat/completions")) {
            return baseUrl.substring(0, baseUrl.length() - "/chat/completions".length());
        }
        // 第3步：其他情况保持原样，兼容填到域名或填到 /v1
        return baseUrl;
    }

    /**
     * 从 Spring AI 响应中提取文本
     *
     * @param response Spring AI 聊天响应
     * @return 模型返回的文本
     */
    private String extractResponseText(ChatResponse response) {
        // 第1步：空响应直接返回空字符串
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        // 第2步：从 AssistantMessage 中读取文本
        String text = response.getResult().getOutput().getText();
        // 第3步：空文本统一转为空字符串
        return text == null ? "" : text;
    }

    /**
     * 从 Spring AI 响应中提取 Token 用量
     *
     * @param response Spring AI 聊天响应
     * @param tokenUsage Token 用量数组，[0] 输入 Token，[1] 输出 Token
     */
    private void parseUsage(ChatResponse response, int[] tokenUsage) {
        // 第1步：响应元数据为空时直接返回
        if (response == null || response.getMetadata() == null) {
            return;
        }
        // 第2步：读取 Spring AI 统一用量对象
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        // 第3步：输入 Token 存在时写入缓存
        if (usage.getPromptTokens() != null) {
            tokenUsage[0] = usage.getPromptTokens();
        }
        // 第4步：输出 Token 存在时写入缓存
        if (usage.getCompletionTokens() != null) {
            tokenUsage[1] = usage.getCompletionTokens();
        }
    }

    /**
     * 流式输出异常时，向前端推送错误信息并关闭 SSE 连接
     *
     * @param emitter SSE 推送对象
     * @param e       异常信息
     */
    private void completeEmitterWithError(SseEmitter emitter, Exception e) {
        safeSend(emitter, SseEmitter.event().name("error").data(buildUserErrorMessage(e)));
        safeErrorComplete(emitter, e);
    }

    /**
     * 安全发送 SSE 事件（前端可能已断开连接，需要捕获异常）
     *
     * @param emitter SSE 推送对象
     * @param event   SSE 事件构建器
     */
    private void safeSend(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try { emitter.send(event); } catch (Exception e) { log.debug("SseEmitter already closed"); }
    }

    /**
     * 安全关闭 SSE 连接（正常完成）
     *
     * @param emitter SSE 推送对象
     */
    private void safeComplete(SseEmitter emitter) {
        try { emitter.complete(); } catch (Exception e) { log.debug("SseEmitter already closed"); }
    }

    /**
     * 安全关闭 SSE 连接（异常完成）
     *
     * @param emitter SSE 推送对象
     * @param e       异常信息
     */
    private void safeErrorComplete(SseEmitter emitter, Exception e) {
        try { emitter.completeWithError(e); } catch (Exception ex) { log.debug("SseEmitter already closed"); }
    }

    /**
     * 构建面向用户的错误提示信息
     * 截断过长的错误详情，添加友好的前缀
     *
     * @param e 异常对象
     * @return 用户可读的错误提示
     */
    private String buildUserErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) { return "聊天服务异常，请稍后重试"; }
        if (message.length() > 500) { message = message.substring(0, 500) + "..."; }
        return "聊天服务异常：" + message;
    }

    /**
     * 安全处理文本（去掉换行符，避免日志格式被打乱）
     *
     * @param text 原始文本
     * @return 处理后的文本，null 转为空字符串
     */
    private String safeText(String text) {
        if (text == null) { return ""; }
        return text.replace("\r", "").replace("\n", "");
    }

    // ==================== 消息持久化 ====================

    /**
     * 保存一条聊天消息到数据库
     * 保存失败时只记录日志，不影响主流程
     *
     * @param sessionId 会话ID
     * @param role      消息角色：user 或 assistant
     * @param content   消息内容
     */
    private void saveMessage(Long sessionId, String role, String content) {
        try {
            ChatMessage msg = new ChatMessage();
            msg.setSessionId(sessionId);
            msg.setRole(role);
            msg.setContent(content);
            chatMessageMapper.insert(msg);
        } catch (Exception e) {
            log.error("Failed to save chat message", e);
        }
    }

    /**
     * 获取某个会话的所有聊天记录（按时间正序）
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    public List<ChatMessage> getSessionMessages(Long sessionId) {
        return chatMessageMapper.findBySessionId(sessionId);
    }

    /**
     * 删除某个会话的所有聊天记录
     *
     * @param sessionId 会话ID
     */
    public void deleteSession(Long sessionId) {
        chatMessageMapper.deleteBySessionId(sessionId);
    }
}
