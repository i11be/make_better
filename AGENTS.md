# AGENTS.md

> 这个文件是本仓库的项目规范，给在这里干活的 AI（和未来的你自己）看。
> 动手前先读完；改了架构就同步改这里，否则以本文件为准。

## 0. 先给方案，等指令再动手（最高优先级）

用户提出问题或需求时：

1. **先想，再说方案。** 动手之前把方案摆出来：改哪些文件、用什么做法、分几步、有什么取舍和风险、最后会得到什么。
2. **不要自己开工。** 不建文件、不改代码、不装依赖、不跑会留下痕迹的命令，直到用户说「开始执行方案」「按这个做」这类话。
3. **只问不改。** 用户只是在问、在聊想法时，就只回答，不动仓库。
4. **跑偏就停。** 执行中发现方案得改，停下来重新说方案，再等指令。
5. 例外只有一种：用户直接点名让做的那件小事（比如「把这条写进 AGENTS.md」），可以当场做。

## 1. 项目是什么

「make me better」是一个**只给自己用**的个人空间，不是公共博客，也没有别人注册：

- **首页**：只有一张世界树图，连顶栏都没有，整页就是这棵树；五个入口（主页、动态、学习、档案、日记）挂在树枝上，不显示条目正文
- **主页（profile）**：头像 + ID + 一句签名 + 四个统计 +「现在」，也就是原来首页那坨资料
- **动态（feed）**：短句 + 最多 9 张图，像 QQ 空间，点图看大图
- **学习（study）**：长笔记，标题 + 分类（八股/算法，可不选）+ 一个标签 + Markdown 正文；
  发布日期保存时自动记，列表能按分类看、按标签分组
- **档案（archive）**：一个主题一个档案（一本书、一个游戏）。档案本身没有正文、只有名字；
  里面挂很多条目，条目用整页写作台写（Markdown，可以插图），阅读时按左边的目录一条条看
- **日记（diary）**：最私密，进这一区要**再输一次密码**
- **相册（album）**：图片放 `data/uploads/albums/{图集}/`，新图先进 `_inbox`（待整理），
  在网页上勾选归类；可移到别的图集、可移进 `.trash`。**以文件夹为准，不落数据库**
- **管理**：写、改、删、改资料、改密码

明确不做：开放注册、多用户、评论、点赞、访客统计、社交分享、关于页（已删）。

## 2. 技术栈（硬约束）

| 层 | 选型 |
| --- | --- |
| 语言 | **Java 21**（JDK 21，LTS） |
| 框架 | **Spring Boot 3.5.x**，Servlet 栈（不用 WebFlux） |
| 构建 | **Maven**，仓库自带 `mvnw` / `mvnw.cmd`，不强求全局装 Maven |
| 视图 | **Thymeleaf** 服务端渲染 + 手写 CSS + 少量原生 JS，无前端框架、无 Node 构建链 |
| 安全 | **Spring Security**：表单登录、BCrypt、HttpSession 会话 |
| 数据库 | **MySQL 8**（`utf8mb4`，库 `make_me_better`，账号 `mmb`）+ **Spring Data JPA** + **Flyway** 管表结构；`dev` profile 可切 H2 文件库离线跑 |
| 图片 | 存本地磁盘 `data/uploads`（数据库只存相对路径），用 JDK 自带 `ImageIO` 处理，**不引第三方图像库** |
| Markdown | **commonmark-java**（默认不渲染裸 HTML） |
| 端口 | 默认 `8090`，只监听 `127.0.0.1`（局域网里连不上，换网络也不影响自己用） |

**不要引入**：React / Vue / Tailwind、Node 前端构建、WebFlux、Redis、MyBatis-Plus、Docker 部署、Lombok。
要换栈，先改这一节，再动代码。

## 3. 运行

```bash
mvnw.cmd spring-boot:run     # → http://127.0.0.1:8090
mvnw.cmd test                # 自检，见第 8 节
mvnw.cmd package             # 出 jar
```

- 配置：`src/main/resources/application.yml`（不含密码）+ `application-local.yml`（数据库账号密码，**不进版本库**）
- profile：默认 `mysql`；`-Dspring-boot.run.profiles=dev` 切到 H2 文件库离线跑
- 上传目录：`app.upload-dir`，默认 `./data/uploads`（头像 `avatars/`，动态配图 `年/月/日/`，
  档案配图 `archives/{档案id}/`，相册 `albums/{图集}/`）
- `deploy/start.cmd`：双击启动；`deploy/open.cmd`：等启动完成后用独立窗口打开

### 改模板时注意（踩过的坑）

1. **不要在 `@{}` 里拼 `${}` 或引用 Spring bean**：Thymeleaf 3.1 会拒绝（`Instantiation of new objects ... forbidden`）。
   要动态 URL 就拼普通字符串：`th:href="${'/feed/' + p.entry.id}"`，或者用参数式 `@{/x/{id}(id=${x.id})}`。
2. **`th:if` 和 `th:replace` 别写在同一个元素上**：replace 优先级更高，`th:if` 会被忽略。
   要条件包含就套一层 `<th:block th:if="...">`。
3. **片段里的脚本**：`th:fragment` 只包含标记了它的那个元素；放在它外面的 `<script>` 会被丢掉（曾经导致整站 JS 没加载）。

## 4. 目录结构

```
AGENTS.md                项目规范（本文件）
README.md                给人看的使用说明
pom.xml
mvnw / mvnw.cmd / .mvn/  Maven Wrapper
deploy/start.cmd, open.cmd
src/main/java/com/makemebetter/
  config/                SecurityConfig、WebConfig
  domain/                User、Entry、EntryKind、StudyCategory、EntryImage、Archive、ArchiveItem、Setting
  repository/            JPA 仓储（唯一写查询的地方）
  service/               EntryService、StudyService、FeedService、ArchiveService、AlbumService、SettingService、
                         FileStorage（落盘）、ImageTools（裁图/缩略图）、DiaryGate、MarkdownService、ViewHelper
  web/                   控制器（Auth/Home/Entry/Feed/Diary/Archive/Album/File/Admin·AdminArchive·AdminSettings）、
                         两个拦截器、GlobalModelAttributes、form/
src/main/resources/
  application*.yml       通用 / dev(H2) / mysql
  db/migration/          Flyway：V1__init.sql、V2__profile_feed_archive.sql、V3__study_category.sql、
                         V4__entry_body_mediumtext.sql、V5__archive_drop_summary.sql
  templates/             fragments/ 公共片段、archive/ 档案页、admin/ 后台
  static/css/style.css   全站样式（唯一 CSS 文件）
  static/js/app.js       主题切换、编辑器、发动态选图、大图查看
src/test/java/           集成测试（见第 8 节）
```

## 5. 数据模型（Flyway 管）

- `users` — `id, username(=主页 ID), password_hash, diary_hash, avatar_path, bio, created_at`；全站只允许一行
- `entries` — `id, kind(study|feed|diary), title, body_md, mood, tags, category, entry_date, created_at, updated_at`
  - 学习的 `category` 存 `bagu` / `algo`，可空（= 未分类）；加分类只改 `StudyCategory` 枚举
  - 学习的 `tags` 只存一个标签；心情只有动态/日记用
  - 学习的 `entry_date` = 发布那天，编辑时不动
  - 动态的 `title` 由正文第一行自动生成，`mood/tags` 留空
  - `kind` 用 VARCHAR + CHECK（不用 MySQL ENUM），为了同一份脚本能在 H2 上跑
- `entry_images` — `id, entry_id(FK, ON DELETE CASCADE), path, width, height, sort`
- `archives` — `id, title, created_at, updated_at`（V5 去掉了 summary：档案不写正文）
- `archive_items` — `id, archive_id(FK, CASCADE), title, body_md, sort, created_at, updated_at`
  - 条目里的图存在 `data/uploads/archives/{档案id}/`，正文用 Markdown 图片引用 `/files/…`
- `settings` — `setting_key, setting_value`（站点名、备用签名、落款、现在）
- **相册没有表**：`data/uploads/albums/{图集}/` 就是图集，`_inbox` 是待整理，`.trash` 是回收站，
  `_thumbs` 是自动生成的缩略图；网页上的「归类」就是移动文件

改表结构：只加新的 `V{n}__*.sql`，**不许改历史脚本**。
**迁移脚本必须在「有数据」的真实 MySQL 上跑一遍**（H2 空库跑通不代表没问题：
V2 曾经因为「先加 CHECK、后改数据」在真库上失败）。

## 6. 安全与权限

- 密码：BCrypt；明文绝不落库、绝不进日志
- 会话：`HttpSession`，`HttpOnly` + `SameSite=Lax`；登录 30 天、日记解锁 30 分钟
- 守卫：**全站都要登录**（`/login`、`/setup`、`/error` 和静态资源除外），没有「能看不能写」的中间态；
  `/diary/**` 在登录之上还要日记密码解锁
- 登录页：整屏海报，点左侧那块橙色芯片才展开输入框（`#gate-open` 这个 checkbox 控制）；
  密码错了面板保持展开，退出后收起。芯片热区钉在图片坐标上，见 style.css 的 `.gate-stage`
- 上传：只收 `image/*`，单张 ≤10MB，一次 ≤9 张；用 ImageIO 解码校验，读不出来就拒绝
- 图片输出：`/files/{*path}` 只认落在 upload 目录内的文件，`..` 越界一律 404
- CSRF：Spring Security 默认开启，表单用 `th:action` 会自动带 token，不许关
- `application-local.yml`、`data/`、`target/` 永不进版本库

## 7. 代码约定

- 类名大驼峰，方法/变量小驼峰，SQL 列名下划线
- 界面文案、注释用中文；标识符用英文
- 时间：库里 `DATE` / `DATETIME`，Java 用 `LocalDate` / `LocalDateTime`，展示统一 `Asia/Shanghai`
- Controller 只做三件事：取数据 → 校验 → 渲染；业务逻辑在 `service/`，SQL 在 `repository/`
- 一个文件超过 250 行就该拆（Admin 已经拆成三个控制器）
- 新增页面必须同时适配深色模式与手机宽度（375px 起）
- 不用 Lombok：手写构造器/getter，或直接用 `record`

## 8. 改完必须自检

```bash
mvnw.cmd test
```

集成测试（MockMvc + H2 的 MySQL 兼容模式）覆盖：
建账号 → 登录 → 改 ID/签名/头像 → 发带图动态 → 空动态被拒 → 建档案 → 加条目 → 前台读取 → 调整顺序
→ 相册上传 / 归类 / 进回收站 → 写学习 → 日记被挡 → 解锁 → 改 → 删 → 退出后后台被挡 → 404。

改完必须真的跑，别只看代码。

**提交**：做完一版就 `git commit` 到本地仓库即可，**不用 `git push`** —— 推不推远端由人自己决定。
提交信息用中文，写清楚改了什么、为什么改。

## 9. 内容与写作约定

- 四条主线：`study` / `feed` / `diary` / `archive`
- 动态的文字是**纯文本**（保留换行，不渲染 Markdown）；学习、日记、档案条目是 Markdown
- 日记不生成公开链接、不导出、不分享
- 首页只有那棵世界树，不显示任何条目正文；条目都在各自的列表页里

## 10. 现在没做、别假装有

- 多用户 / 开放注册（ID 和头像只是「单人的身份」）
- 评论、点赞、关注
- 档案封面图、档案分类
- 图片压缩参数可调、EXIF 方向修正
- 全文搜索、备份按钮（现在是 `mysqldump` + 拷 `data/uploads`）
- Docker / 云部署
