# RAG 知识库

一个用 Spring Boot 写的本地 RAG（Retrieval-Augmented Generation，检索增强生成）知识库问答系统。

你把资料（PDF / Word / Markdown / TXT / 网页链接）丢进去，它把资料切成小块、转成向量存起来；
你提问时，它先去资料里找最相关的几段，再把这些段落连同问题一起交给大模型，让模型**基于你的资料**回答，
并且告诉你答案是从哪几段资料来的。

---

## 一、RAG 到底在干什么

大模型（比如 DeepSeek）本身不知道你的私人资料。硬要问它，它要么答不上来，要么开始编。

RAG 的思路很朴素：

```
你上传资料  →  切成小段（chunk）  →  每段转成一串数字（向量/embedding）  →  存进数据库
                                                                          ↓
你提问      →  问题也转成向量    →  和库里所有向量比相似度  →  取最像的几段
                                                                          ↓
                            把「这几段资料 + 你的问题」一起发给大模型  →  模型照着资料回答
```

两个关键概念：

- **embedding（向量）**：把一段文字变成一串数字，比如 1024 个浮点数。语义越接近的文字，它们的向量在空间里越靠近。这样"意思相近"就能用数学算出来了。
- **余弦相似度**：比较两个向量"方向"有多接近，取值 -1 到 1，越接近 1 越相似。用它来给所有资料块打分排序。

---

## 二、技术栈

| 部分 | 选型 | 说明 |
|---|---|---|
| 语言 / 框架 | Java 21 + Spring Boot 3.5.16 | |
| Web | Spring MVC | 提供 REST 接口 + 静态页面 |
| 数据访问 | Spring Data JPA + Hibernate | 用 Java 对象操作数据库，不用手写 SQL |
| 数据库 | HSQLDB 2.7.4（文件模式） | 嵌入式，数据存本地文件，不用单独装 MySQL |
| PDF 解析 | Apache PDFBox 3.0.5 | |
| Word 解析 | Apache POI 5.4.1 | |
| 网页抓取 | Jsoup 1.21.1 | |
| Embedding | 硅基流动 `BAAI/bge-m3`（OpenAI 兼容格式） | 也可用本地假向量，无需联网 |
| 大模型 | DeepSeek `deepseek-chat` | OpenAI 兼容格式 |
| 前端 | 原生 HTML/CSS/JS | 无框架、无构建步骤 |

> **为什么不用 H2？** 项目最初用的 H2，但 H2 2.x 打开一个已存在的库时会调用
> `java.nio.file.Files.isWritable()` 判断可写性，一旦返回 `false` 就把库强制降级成只读，
> 之后任何写入都报 `The database is read only ... [90097]`。
> 在部分受限环境（含本项目的开发沙箱）下该方法恒返回 `false`，导致"新建的库第一次能写，重启后就只读"。
> HSQLDB 没有这个检查，所以换掉了。

---

## 三、快速开始

### 1. 环境要求

- JDK 21（本项目用 `JAVA_HOME` 定位，命令行 `java -version` 需是 21）
- 无需安装 Maven —— 项目自带 Maven Wrapper（`mvnw` / `mvnw.cmd`）

### 2. 启动

Windows：

```bat
mvnw.cmd spring-boot:run
```

macOS / Linux：

```bash
./mvnw spring-boot:run
```

启动后打开浏览器访问 **http://localhost:8081**

### 3. 什么都不配也能用

默认配置走**本地假向量**（`rag.embedding.provider=fake`），不联网、不需要任何 API Key。
上传资料、检索、看引用来源全都正常，只是"语义相似"的判断比较粗糙（靠词面重叠）。

这个模式适合先把流程跑通、把界面摸熟。

---

## 四、配置真实的 API Key

想要真正的语义检索和真正的模型回答，需要两个 Key：

| 用途 | 申请地址 | 配置项 |
|---|---|---|
| Embedding（文字转向量） | https://cloud.siliconflow.cn （有免费额度） | `rag.embedding.api-key` |
| 大模型（生成回答） | https://platform.deepseek.com | `rag.llm.api-key` |

在**项目根目录**新建 `application-local.properties`（该文件已被 `.gitignore` 排除，不会被提交）：

```properties
# 切换到真实的 embedding 服务
rag.embedding.provider=real
rag.embedding.api-key=sk-你的硅基流动Key

# DeepSeek 的 Key
rag.llm.api-key=sk-你的DeepSeekKey
```

然后重启应用。

> ⚠️ **换了 embedding 模型后，旧向量作废**。因为不同模型产出的向量不在同一个坐标系里，不能互相比较。
> 换模型后请把 `data/` 目录删掉，重新上传资料。

> 💡 没配 DeepSeek Key 时，问答接口不会报 500，而是返回一句中文提示 + 已经检索到的资料片段，
> 你仍然能看到"资料里有哪些相关内容"。

---

## 五、怎么用

打开 http://localhost:8081 后：

**左侧「知识库」** —— 三个标签页

- **文件**：拖拽或点击上传 PDF / DOCX / TXT / MD / CSV / JSON
- **网页**：粘贴一个网址，抓取正文入库
- **文本**：直接粘贴一段文字，起个标题存进去

上传后下方列表会显示所有已入库的文档（标题、来源类型、字数、切成了几块），每条右侧可以删除。

**右侧对话框** —— 直接输入问题，回车发送。

回答下方会列出**引用来源**，点开可以看到具体是哪一段资料、相似度多少。
如果模型没被调用（没配 Key），气泡会是橙色的警告样式，但仍然带着检索结果。

---

## 六、目录结构

```
src/main/java/com/example/rag/
├── RagApplication.java             启动类
├── config/
│   ├── RagProperties.java          所有 rag.* 配置项的类型化绑定
│   └── PropertiesConfig.java       把上面的类注册成 Bean
├── embedding/                      文字 → 向量
│   ├── EmbeddingClient.java          接口
│   ├── HashingEmbeddingClient.java   本地假向量（哈希词袋）
│   ├── OpenAiCompatibleEmbeddingClient.java  在线 API 实现
│   ├── EmbeddingConfig.java          按配置选实现
│   ├── EmbeddingException.java
│   └── VectorUtils.java              余弦相似度 / L2 归一化 / 序列化
├── domain/                         数据库实体
│   ├── Document.java                 一份文档
│   └── Chunk.java                    文档切出来的一块（含向量）
├── repository/
│   ├── DocumentRepository.java
│   └── ChunkRepository.java
├── retrieval/                      向量检索
│   ├── RetrievedChunk.java           检索结果（带分数）
│   └── RetrievalService.java         全表算余弦 + 排序 + 阈值过滤
├── ingest/                         资料入库
│   ├── TextExtractor.java            PDF / DOCX / 纯文本 抽文字
│   ├── WebFetcher.java               抓网页正文
│   ├── TextChunker.java              长文本切块
│   ├── IngestionService.java         串起「解析→切块→向量化→存库」
│   └── IngestException.java
├── llm/                            大模型
│   ├── LlmClient.java                接口
│   ├── OpenAiCompatibleLlmClient.java  DeepSeek 实现
│   ├── LlmConfig.java                没配 Key 时返回一个"未配置"的空实现
│   └── LlmException.java
├── chat/                           问答编排
│   ├── PromptBuilder.java            把「资料 + 问题」拼成提示词
│   ├── ChatService.java              检索 → 拼提示词 → 调模型 → 组装响应
│   └── ChatResponse.java
└── web/                            HTTP 接口层
    ├── HealthController.java         健康检查
    ├── DocumentController.java       文档管理
    ├── ChatController.java           问答
    └── GlobalExceptionHandler.java   统一错误响应
```

前端只有一个文件：`src/main/resources/static/index.html`

---

## 七、接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查 |
| GET | `/api/documents` | 列出所有文档（按创建时间倒序） |
| POST | `/api/documents/upload` | 上传文件（multipart，字段名 `file`） |
| POST | `/api/documents/url` | 抓取网页入库，body `{"url": "https://..."}` |
| POST | `/api/documents/text` | 纯文本入库，body `{"title": "...", "text": "..."}` |
| DELETE | `/api/documents/{id}` | 删除文档及其所有分块 |
| POST | `/api/chat` | 问答，body `{"question": "...", "topK": 5}` |

`/api/chat` 返回示例：

```json
{
  "answer": "InnoDB 使用 B+ 树作为索引结构 [1]。",
  "sources": [
    {
      "index": 1,
      "documentId": 2,
      "documentTitle": "数据库笔记",
      "chunkIndex": 0,
      "content": "InnoDB 使用 B+ 树作为索引结构……",
      "score": 0.1709
    }
  ],
  "usedLlm": true,
  "elapsedMs": 1830
}
```

`usedLlm=false` 表示没有真正调用大模型（没配 Key，或检索不到资料），此时 `answer` 是一句提示语。

---

## 八、配置项

全部在 `src/main/resources/application.properties`：

```properties
# embedding
rag.embedding.provider=fake              # fake | real
rag.embedding.base-url=https://api.siliconflow.cn/v1
rag.embedding.model=BAAI/bge-m3
rag.embedding.api-key=
rag.embedding.dimension=1024

# 切块
rag.chunk.size=500                       # 每块最大字符数
rag.chunk.overlap=100                    # 相邻块重叠字符数

# 检索
rag.retrieval.top-k=5                    # 取最相关的几段
rag.retrieval.min-score=0.2              # 相似度阈值，低于它的直接丢弃

# 大模型
rag.llm.base-url=https://api.deepseek.com/v1
rag.llm.model=deepseek-chat
rag.llm.api-key=
rag.llm.temperature=0.2
rag.llm.max-tokens=1500
```

---

## 九、几个设计上的取舍

**为什么切块要重叠（overlap）？**
一段话如果正好在句子中间被切开，两半各自都缺上下文。让相邻块重叠 100 个字符，可以保证关键句子至少在一个块里是完整的。

**为什么检索是"全表扫描"？**
现在把所有向量读进内存，逐个算余弦相似度再排序。几千个分块以内完全够用，代码也简单。数据量大了（十万级以上）要换成专门的向量数据库（pgvector、Milvus、Qdrant 之类），它们有 ANN 索引。

**为什么没配 Key 也能启动？**
`LlmConfig` 在没配 Key 时返回一个 `UnconfiguredLlmClient` 空实现，调用它的 `chat()` 会抛带中文说明的异常，而不是让整个应用启动失败。这样你可以先玩检索、看资料，等拿到 Key 再开启生成。

**为什么模型调用失败不抛 500？**
`ChatService` 捕获 `LlmException` 后降级成正常响应（`usedLlm=false` + 提示语 + 检索结果）。模型挂了不该让你连"资料里有什么"都看不到。

**为什么 `Usage` 的字段要加 `@JsonProperty`？**
DeepSeek 返回的是 `prompt_tokens` 这种下划线命名，Java 字段是 `promptTokens`。不写注解的话 Jackson 绑不上，字段永远是 null，而且不会报错。

---

## 十、开发命令

```bash
# 跑全部测试
mvnw.cmd test

# 打包
mvnw.cmd package

# 跳过测试打包
mvnw.cmd package -DskipTests
```

> 如果你的机器上 `~/.m2` 不可写（比如在受限沙箱里），可以指定本地仓库位置：
> `mvnw.cmd -Dmaven.repo.local=./.mvn-home/repository test`

---

## 十一、已知限制

- 检索是全表扫描，适合几千个分块的学习/小规模场景，不适合大规模生产
- 不支持扫描版 PDF（纯图片，需要 OCR）
- 不支持 `.doc` 老格式（只支持 `.docx`）
- 单个上传文件上限 50MB
- 没有用户系统，所有资料是共享的
- 没有流式输出，回答是一次性返回的

---

## 十二、在 VS Code 里打开这个项目

1. 打开 VS Code
2. 菜单 **File → Open Folder...**（文件 → 打开文件夹）
3. 选择这个 `RAG` 文件夹（**不是**选里面的某个文件）
4. 推荐装两个扩展：
   - **Extension Pack for Java**（微软官方，提供 Java 补全、调试、运行）
   - **Spring Boot Extension Pack**（提供 Spring 配置补全）
5. 打开后 VS Code 会自动识别这是 Maven 项目（左下角会显示"Java: Ready"之类的状态）
6. 运行方式二选一：
   - 打开 `src/main/java/com/example/rag/RagApplication.java`，点 `main` 方法左边的 ▷ 三角
   - 或者在终端里跑 `mvnw.cmd spring-boot:run`

> 如果 VS Code 报 Java 版本不对，按 `Ctrl+Shift+P` → 输入 `Java: Configure Java Runtime`，
> 确认用的 JDK 是 21。
