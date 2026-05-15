# WUJO RAG 智能知识库系统

基于 RAG（Retrieval-Augmented Generation，检索增强生成）的智能知识库问答系统。项目支持文档上传、文本解析、自动切片、向量化入库、知识库召回、AI 流式问答、历史对话、模型供应商切换和用量统计，适合作为学习 RAG 全流程、Spring Boot + Vue 前后端分离、Spring AI 接入大模型的开源项目。

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M6-green)
![Vue](https://img.shields.io/badge/Vue-3.4-brightgreen)
![Qdrant](https://img.shields.io/badge/Qdrant-Vector%20Database-purple)
![License](https://img.shields.io/badge/License-MIT-yellow)

## 项目特点

- 文档知识库：支持创建知识库、上传文件、查看文件处理状态、删除知识库和文件。
- 文档解析：后端使用 Apache Tika 解析 PDF、DOCX、TXT 等文档内容。
- 文本切片：将长文档拆分成适合向量检索和大模型上下文的文本块。
- 向量入库：使用 Embedding 模型生成向量，并批量写入 Qdrant。
- 文本落库：切片文本写入 MySQL，召回后可根据切片 ID 回查原文内容。
- 混合召回：支持向量召回与关键词检索，提升知识库问答命中率。
- 流式问答：后端通过 SSE 推送大模型输出，前端逐段展示回答内容。
- 模型管理：支持配置 OpenAI 兼容接口的大模型供应商，并在页面中切换激活模型。
- Spring AI：后端使用 Spring AI OpenAI Starter 调用 OpenAI 兼容聊天模型。
- 对话历史：聊天消息保存到 MySQL，可按会话查看历史记录。
- 用量统计：记录模型调用、耗时、Token 等基础统计数据。
- 前后端分离：后端 Spring Boot，前端 Vue 3 + Vite + Element Plus。

## 技术栈

### 后端

| 技术 | 说明 |
| --- | --- |
| Java 17 | 后端运行环境 |
| Spring Boot 3.2.5 | 后端主框架 |
| Spring AI OpenAI Starter 1.0.0-M6 | 调用 OpenAI 兼容大模型 |
| MyBatis | 数据库访问 |
| MySQL 8.x | 业务数据、用户、知识库、文件、切片、聊天记录存储 |
| Redis | 缓存能力预留 |
| Qdrant | 向量数据库 |
| Apache Tika | 文档内容解析 |
| JWT | 登录认证 |
| Lombok | 简化实体和样板代码 |
| SSE | 服务端流式推送 |

### 前端

| 技术 | 说明 |
| --- | --- |
| Vue 3 | 前端框架 |
| Vite | 前端构建工具 |
| Vue Router | 路由管理 |
| Pinia | 状态管理 |
| Axios | HTTP 请求 |
| Element Plus | UI 组件库 |
| Markdown-It | Markdown 渲染 |

## 项目结构

```text
wujo_rag
├── database
│   └── init.sql
├── rag-server
│   ├── pom.xml
│   └── src
│       └── main
│           ├── java
│           │   └── com.rag
│           │       ├── ai
│           │       ├── common
│           │       ├── config
│           │       ├── controller
│           │       ├── dto
│           │       ├── entity
│           │       ├── mapper
│           │       ├── rag
│           │       ├── service
│           │       └── vo
│           └── resources
│               ├── application.yml
│               └── mapper
├── rag-web
│   ├── package.json
│   └── src
│       ├── api
│       ├── layouts
│       ├── router
│       ├── stores
│       ├── styles
│       ├── utils
│       └── views
├── .gitignore
├── LICENSE
└── README.md
```

## 核心流程

### 文档入库流程

1. 用户在前端选择知识库并上传文件。
2. 后端保存文件到本地上传目录。
3. 后端在 MySQL 中创建文件记录，状态为 `UPLOADED`。
4. 后台线程开始处理文件，状态更新为 `PROCESSING`。
5. `DocumentParser` 使用 Apache Tika 将文件解析为纯文本。
6. `TextSplitter` 将长文本切成多个文本片段。
7. `FileService` 将切片文本批量写入 MySQL。
8. `EmbeddingService` 调用 Embedding 服务生成向量。
9. `QdrantService` 将向量点批量写入 Qdrant。
10. 文件处理完成后，状态更新为 `COMPLETED`。

### 知识库问答流程

1. 用户在聊天页面选择知识库并输入问题。
2. 后端保存用户问题到聊天记录。
3. 系统将用户问题向量化。
4. 系统从 Qdrant 检索相似文本切片。
5. 系统结合关键词检索结果，提高召回准确率。
6. 系统根据召回内容构造 RAG Prompt。
7. `ChatService` 通过 Spring AI 调用当前激活的大模型。
8. 后端通过 SSE 将回答内容流式推送给前端。
9. 前端逐段渲染回答内容。
10. 后端保存 AI 回答和调用统计。

## 环境要求

| 环境 | 建议版本 |
| --- | --- |
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| MySQL | 8.x |
| Qdrant | 1.x |
| Ollama | 可选，用于本地 Embedding |

## 快速启动

### 1. 克隆项目

```bash
git clone https://github.com/wujo-Elwood/easy-rag.git
cd wujo_rag
```

### 2. 初始化数据库

先创建 MySQL 数据库并执行初始化脚本：

```bash
mysql -u root -p < database/init.sql
```



### 3. 启动 Qdrant

如果你本地安装了 Docker，可以直接运行：

```bash
docker run -p 6333:6333 -p 6334:6334 -v ./qdrant_storage:/qdrant/storage qdrant/qdrant
```

### 4. 准备 Embedding 服务

项目默认使用 Ollama 的 Embedding 接口：

```bash
ollama pull bge-m3
ollama serve
```

默认 Embedding 地址：

```text
http://localhost:11434/api/embeddings
```

如果你使用其他 Embedding 服务，可以在后端配置中修改 `embedding.api-url` 和 `embedding.model`。

### 5. 修改后端配置

后端配置文件位置：

```text
rag-server/src/main/resources/application.yml
```

建议本地运行时重点检查以下配置：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/rag_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: your_mysql_username
    password: your_mysql_password

embedding:
  api-url: ${EMBEDDING_API_URL:http://localhost:11434/api/embeddings}
  model: ${EMBEDDING_MODEL:bge-m3}

qdrant:
  host: ${QDRANT_HOST:localhost}
  port: ${QDRANT_PORT:6333}
  collection: ${QDRANT_COLLECTION:rag_chunks}
  upsert-batch-size: ${QDRANT_UPSERT_BATCH_SIZE:100}

file:
  upload-dir: ${FILE_UPLOAD_DIR:D:/xxx/xxxx/uploads}

jwt:
  secret: ${JWT_SECRET:please-change-this-secret}
  expiration: 86400000
```

### 6. 启动后端

```bash
cd rag-server
mvn spring-boot:run
```

后端默认地址：

```text
http://localhost:8080
```

### 7. 启动前端

```bash
cd rag-web
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

## 模型供应商配置

项目支持 OpenAI 兼容格式的大模型接口。供应商信息存储在 `ai_model_provider` 表中，也可以在前端模型设置页面维护。

字段说明：

| 字段 | 说明 |
| --- | --- |
| name | 供应商名称，例如 GPT、DeepSeek、Mimo |
| base_url | OpenAI 兼容接口地址，通常以 `/v1` 结尾 |
| api_key | 模型服务 API Key |
| model | 模型名称 |
| is_active | 是否为当前激活模型 |

示例配置：

```text
name: GPT
base_url: https://api.openai.com/v1
api_key: your_api_key_here
model: gpt-4o-mini
is_active: 1
```

说明：后端会读取当前激活的供应商配置，并通过 Spring AI 动态创建聊天模型客户端。

## 主要接口

### 认证接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |

### 知识库接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/kb` | 创建知识库 |
| GET | `/api/kb` | 查询当前用户可见知识库 |
| GET | `/api/kb/{id}` | 查询知识库详情 |
| DELETE | `/api/kb/{id}` | 删除知识库 |

### 文件接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/file/upload` | 上传文件 |
| GET | `/api/file/list/{kbId}` | 查询知识库下的文件 |
| GET | `/api/file/{id}` | 查询文件详情 |
| POST | `/api/file/{id}/reprocess` | 重新处理文件 |
| DELETE | `/api/file/{id}` | 删除文件 |

### 聊天接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/chat/send` | 普通非流式聊天 |
| POST | `/api/chat/stream` | SSE 流式聊天 |
| GET | `/api/chat/history/{sessionId}` | 查询会话历史 |
| DELETE | `/api/chat/session/{sessionId}` | 删除会话 |
| POST | `/api/chat/recall-test` | 召回测试 |
| POST | `/api/chat/feedback` | 提交回答反馈 |

### 模型供应商接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/model-provider/list` | 查询供应商列表 |
| GET | `/api/model-provider/active` | 查询当前激活供应商 |
| POST | `/api/model-provider` | 新增供应商 |
| PUT | `/api/model-provider/{id}` | 修改供应商 |
| DELETE | `/api/model-provider/{id}` | 删除供应商 |
| PUT | `/api/model-provider/{id}/activate` | 激活供应商 |

### 用量统计接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/usage/stats` | 查询今日统计和近 7 天趋势 |

## 数据库表说明

| 表名 | 说明 |
| --- | --- |
| sys_user | 用户表 |
| kb_knowledge_base | 知识库表 |
| kb_file | 文件表 |
| kb_chunk | 文本切片表 |
| ai_chat_message | 聊天消息表 |
| ai_model_provider | 模型供应商表 |
| ai_usage_log | 模型调用统计表 |
| ai_feedback | 回答反馈表 |

## 前端页面

| 页面 | 说明 |
| --- | --- |
| 登录页 | 用户登录和注册 |
| 知识库页 | 创建、查看、删除知识库 |
| 文件页 | 上传文件、查看处理状态、重新处理和删除文件 |
| AI 聊天页 | 基于知识库进行 RAG 问答，支持流式输出和历史消息 |
| 模型设置页 | 配置、切换 OpenAI 兼容模型供应商 |
| 统计页 | 查看模型调用和用量趋势 |

## 适合新手阅读的代码入口

如果你想从头到尾理解代码逻辑，建议按下面顺序看：

1. `rag-web/src/views/kb/KbView.vue`：前端知识库页面入口。
2. `rag-web/src/views/file/FileView.vue`：前端文件上传入口。
3. `rag-server/src/main/java/com/rag/service/FileService.java`：文件保存、解析、切片、入库、向量写入主流程。
4. `rag-server/src/main/java/com/rag/rag/DocumentParser.java`：文档解析逻辑。
5. `rag-server/src/main/java/com/rag/rag/TextSplitter.java`：文本切片逻辑。
6. `rag-server/src/main/java/com/rag/ai/EmbeddingService.java`：Embedding 向量生成逻辑。
7. `rag-server/src/main/java/com/rag/rag/QdrantService.java`：Qdrant 集合检查、向量写入、召回查询逻辑。
8. `rag-web/src/views/chat/ChatView.vue`：前端聊天和流式输出入口。
9. `rag-server/src/main/java/com/rag/controller/ChatController.java`：聊天接口入口。
10. `rag-server/src/main/java/com/rag/ai/ChatService.java`：RAG 召回、Prompt 拼接、Spring AI 调用和 SSE 推送主流程。

## 常见问题

### 1. 聊天接口返回 503

通常是模型供应商不可用，重点检查：

- `base_url` 是否正确，OpenAI 兼容接口通常是 `https://xxx/v1`。
- `api_key` 是否有效。
- `model` 名称是否和供应商后台一致。
- 供应商是否支持 `/chat/completions`。
- 供应商是否支持流式输出。

### 2. 文件一直是 PROCESSING

通常是后台处理失败，重点检查：

- 后端日志是否有文件解析异常。
- Ollama 或 Embedding 服务是否启动。
- Qdrant 是否启动。
- MySQL 表结构是否初始化成功。
- 上传目录是否有写入权限。

### 3. 能聊天但没有知识库召回

重点检查：

- 文件状态是否已经是 `COMPLETED`。
- `kb_chunk` 表是否有切片数据。
- Qdrant 集合中是否有向量点。
- Embedding 模型是否和入库时一致。
- `rag.top-k` 是否过小。

### 4. 前端请求后端失败

重点检查：

- 后端是否运行在 `8080` 端口。
- 前端 Vite 代理是否配置正确。
- 登录 Token 是否过期。
- 浏览器控制台是否存在跨域、401 或 500 错误。

## 构建命令

### 后端编译

```bash
cd rag-server
mvn clean package -DskipTests
```

### 前端构建

```bash
cd rag-web
npm run build
```

## License

本项目使用 MIT License，详见 [LICENSE](./LICENSE)。

## 说明

这个项目更适合作为 RAG 学习项目和二次开发脚手架。如果用于生产环境，建议继续补充权限隔离、文件安全扫描、接口限流、模型调用重试、敏感词过滤、完整测试用例、日志脱敏和配置中心等能力。
