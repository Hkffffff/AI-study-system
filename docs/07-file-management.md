# 07 文件管理设计

## 1. 原则

1. **原件是事实来源**：原件原样保存，永不修改或覆盖；所有缩略图、页渲染图和 AI 输入图都是可以重新生成的派生物。
2. **元数据放 MySQL，二进制放存储层**：数据库中没有 BLOB。
3. **不信任客户端**：文件类型由服务端通过魔数检测；客户端声明的 MIME 只做记录。
4. **存储可替换**：业务代码只依赖 `StorageService`，数据库只存相对的 `storage_key`。

## 2. 存储抽象

```java
public interface StorageService {
    String backend();                                              // "local"
    void put(String key, Path source, String contentType);         // 原子写入
    InputStream open(String key);
    boolean exists(String key);
    void delete(String key);
}
```

### 2.1 LocalFileStorage（MVP）

- 根目录：`APP_STORAGE_LOCAL_ROOT`（默认 `./data/storage`，已加入 `.gitignore`）。
- 写入：先写到同一卷上的临时文件，再原子移动（`ATOMIC_MOVE`），避免留下不完整的文件。
- 路径安全：`root.resolve(key).normalize()` 必须以 `root` 开头，否则拒绝（防止路径穿越）。key 只由系统生成，不包含用户输入。

### 2.2 Key 规则

| 用途 | Key | 说明 |
|---|---|---|
| 原件 | `originals/{yyyy}/{MM}/{uuid}.{ext}` | 扩展名来自**检测出的**类型；不含空间或归档信息，因此移动归档不需要移动文件 |
| 缩略图 | `derived/{fileId}/thumb.jpg` | 图片缩略图，或 PDF 第 1 页的缩略图 |
| PDF 页图 | `derived/{fileId}/p{n}-{thumb|view}.jpg` | 按需渲染后缓存 |
| AI 输入图 | `derived/{fileId}/ai/{p{n}|img}-{maxEdge}.{jpg|png}` | 按模型的能力参数缓存 |

`derived/` 可以整个删除，系统会按需重新生成。

### 2.3 以后切换到 S3 / MinIO / OSS

新增 `S3StorageService` 实现，按配置选择；新文件写入时 `storage_backend` 为 `s3`。迁移旧文件时逐个复制对象，再更新 `storage_backend`（key 不变）。

## 3. 上传

### 3.1 支持的类型与限制（MVP）

| 类型 | 检测魔数 | 单文件大小 | 其他限制 |
|---|---|---|---|
| JPEG | `FF D8 FF` | ≤ 20 MB | 像素 ≤ 5000 万（读取文件头判断，防止解压炸弹） |
| PNG | `89 50 4E 47 0D 0A 1A 0A` | ≤ 20 MB | 同上 |
| WebP | `RIFF....WEBP` | ≤ 20 MB | 同上 |
| PDF | `%PDF-` | ≤ 50 MB | 见 §4 |

- 单次请求最多 20 个文件，总大小不超过 200 MB（`spring.servlet.multipart` 配置）。
- 不支持：HEIC/HEIF（已确认，README C8；上传时提示「请在手机相机设置中选择『兼容性最佳』（JPG），或先转换为 JPG」）、GIF、BMP、TIFF、Word/PPT 等。
- 限制值都可以通过 `app.file.*` 配置。

### 3.2 上传流程

```
每个 multipart part:
 1. 流式写入临时文件，同时计算 SHA-256 和大小（超过上限立即中止）
 2. 读前 16 字节 → 检测类型 → 检查白名单
 3. 按类型深度校验：
      图片：读取文件头中的尺寸和 EXIF 方向 → 校验像素上限 → 记录校正后的 width/height
      PDF ：用 PDFBox 打开（仅临时文件模式）→ 检查加密 → 读取页数 → 检查页数上限
 4. 生成 storage_key → StorageService.put
 5. INSERT stored_file（同一事务中校验 archive 与 space 是否一致）
 6. 第 5 步失败 → 删除第 4 步写入的对象（补偿）
 7. 在 finally 中删除临时文件
```

- 响应：`200 {results:[{originalFilename, ok, file?, error?{code,message}, duplicateOf?[]}]}`，允许部分成功，前端逐个显示结果。
- **重复提示**：同一空间内已存在相同 `sha256` 的文件时，仍然保存，但在响应中返回 `duplicateOf`，前端提示「可能重复上传」。
- 原始文件名只用于显示：去掉路径部分和控制字符，截断到 255 字符；从不用于构造存储路径。

### 3.3 读取与下载

| 接口 | 响应头 |
|---|---|
| `GET /files/{id}/content` | `Content-Type` = **检测出的** `mime_type`；`Content-Disposition: inline; filename*=UTF-8''…`；`X-Content-Type-Options: nosniff`；`ETag` = sha256；`Cache-Control: private, max-age=31536000, immutable`；支持 `Range`（方便浏览器查看大 PDF） |
| `GET /files/{id}/thumbnail`、`/pages/{n}/image` | `image/jpeg`，按需生成并缓存 |

### 3.4 移动与删除

- 移动：只能移到同一空间内的其他归档，或设为「未归档」。不支持跨空间移动（MVP）。
- 删除：软删除（`deleted_at`）。**被未删除题目的 `question_source` 引用时拒绝删除**（`FILE_IN_USE`），界面列出引用它的题目。
- 物理清理：MVP 不实现。以后提供维护任务：清理软删除超过 30 天的原件，以及数据库中没有记录的孤儿对象。

## 4. PDF MVP 支持范围【评审 R4】

### 4.1 支持 ✅

| 能力 | 说明 |
|---|---|
| 上传 | 文字版 PDF 和扫描版 PDF 都可以；≤ 50 MB；**≤ 200 页**（可配置） |
| 原件查看 | 在新标签页用浏览器内置的 PDF 查看器打开原件（`/content`，支持 Range） |
| 按页浏览 | 后端用 PDFBox 按需渲染：缩略图（长边 300px）和查看图（150 DPI，长边 ≤ 2000px），结果缓存 |
| 从页面建题 | 在查看器中选择 1 页或多页作为题目来源，每页单独标注角色；**每道题的来源总数（图片 + PDF 页）不超过 6** |
| AI 分析 | 选中的页面**先渲染成图片再发送给模型**（200 DPI，再按模型能力缩放），各家 Provider 行为一致 |
| 追溯 | `question_source.page_no` 记录页码；分析的输入快照中记录每一页的来源文件、页码和发送时的尺寸 |

### 4.2 不支持 ❌（MVP 之后再考虑）

| 不支持 | 原因或替代方案 |
|---|---|
| 整份 PDF 一次性分析或总结 | 成本高，而且偏离「单题分析」的核心流程；以后可以在归档级对话中实现 |
| AI 自动识别某一页有哪些题并拆分 | 已确认暂时不做；MVP 用「位置提示」替代 |
| 在页面上框选题目区域 | 数据库已预留 `question_source.region` |
| 原生 PDF 输入（把 PDF 文件直接发送给模型） | 已预留 `DocumentPart` 和能力 `PDF`；MVP 统一转成图片，简单可控，也方便逐页标注角色 |
| 提取文字层、全文检索、OCR 建立索引 | 不在 MVP 范围 |
| 带打开密码的加密 PDF | 上传时拒绝（`PDF_ENCRYPTED`）；只有权限密码、不需要密码就能打开的 PDF 可以接受 |
| 编辑、批注、合并、拆分 PDF | 原件不可修改 |
| PDF 附件、XFA 表单、Word/PPT 转换 | 不支持 |

### 4.3 渲染资源控制

- PDFBox 使用「仅临时文件」的内存模式，避免大文件占满堆内存。
- 渲染并发由信号量限制为 2；单页渲染超时 20 秒（超时后该页返回错误占位图）。
- 首次查看某一页需要大约 0.5–1 秒，之后命中缓存。

## 5. AI 输入预处理（AiInputPreparer）

依据所选模型的 `ImageLimits`（06 §4）处理每个来源：

```
图片来源：
  如果 mime ∈ 模型支持的类型，大小 ≤ maxBytes，长边 ≤ maxLongEdgePx，且 EXIF 方向正常
      → 直接发送原始字节（画质最好）
  否则 → 解码 → 按 EXIF 方向旋转 → 按比例缩放到长边 ≤ maxLongEdgePx
        → 编码为 JPEG（q=0.9；截图类 PNG 如果体积允许就保留 PNG）
        → 仍然超过 maxBytes 时逐步降低质量（最低 0.7），还不够就报错
PDF 页来源：
  以 200 DPI 渲染 → 同样的缩放和编码 → 缓存到 derived/{fileId}/ai/
```

- 图片数量超过模型的 `maxImagesPerRequest` 时提前拒绝（不静默丢弃）。
- 每个来源的实际发送参数（尺寸、字节数、是否经过转码）写入 `input_snapshot`。
- 已知风险：缩小图片可能让手写细节变模糊。缓解办法：优先选择 `maxLongEdgePx` 更大的模型；以后通过区域框选只发送题目所在区域。

## 6. 依赖

| 依赖 | 理由 |
|---|---|
| Apache PDFBox 3.x | PDF 校验、页数、渲染 |
| TwelveMonkeys `imageio-jpeg` / `imageio-webp` | JDK 自带的 ImageIO 读不了 CMYK JPEG（扫描件中常见），也读不了 WebP |
| `metadata-extractor` | 读取 EXIF 方向（手机照片经常是旋转存储的） |

魔数检测是手写的（只有 4 种类型），不引入 Tika。

## 7. 备份

个人数据至少要备份两部分：

1. `mysqldump --single-transaction` 导出数据库；
2. 打包 `APP_STORAGE_LOCAL_ROOT/originals/`（`derived/` 可以不备份）。

M7 提供备份和恢复的脚本或说明。

## 8. 测试

- **魔数检测**：每种合法类型；把 `.exe` 改名成 `.jpg`；声明的 MIME 与实际不符；空文件；截断的文件。
- **大小和像素上限**：刚好在边界上、超过 1 字节；像素炸弹图片（文件头声明巨大尺寸）。
- **PDF**：正常、加密、页数超限、损坏的 PDF。
- **存储**：路径穿越 key 被拒绝；DB 写入失败后对象被删除；原子写入。
- **删除保护**：被引用的文件无法删除。
- **预处理**：给定能力参数，断言输出的尺寸、格式和字节数；EXIF 旋转后的方向正确。
