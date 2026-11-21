# make me better

一个只给自己用的个人空间：**动态**、**学习**、**档案**、**日记**。

没有注册、没有别人、没有评论区。数据在你自己电脑的 MySQL 里，图片在 `data/uploads`。

---

## 现在有什么

| 栏目 | 是什么 |
| --- | --- |
| 首页 | 一张世界树图，五个入口（主页、动态、学习、档案、日记）挂在树枝上 |
| 主页 `/profile` | 头像 + ID + 一句签名 + 四个入口的统计 +「现在」 |
| **动态** `/feed` | 短句 + 最多 9 张图，像 QQ 空间；点图看大图（← → 切换，Esc 关） |
| **学习** `/study` | 长笔记，Markdown |
| **档案** `/archive` | 一个主题一个档案（一本书、一个游戏），里面挂很多条目，条目也是 Markdown |
| **日记** `/diary` | 单独的密码，30 分钟自动上锁 |
| 管理 `/admin` | 写、改、删、改资料（ID/头像/签名）、改两个密码 |

整站都在登录后面：连首页都进不去，必须先输用户名和密码。日记在那之后还要再输一次自己的密码。

深色是默认主题，内页右上角可以切浅色。

## 怎么启动

**最简单**：双击 `deploy\start.cmd`。

**手动**：

```bash
mvnw.cmd spring-boot:run
```

打开 http://127.0.0.1:8090 。

第一次打开会自动跳到 `/setup`，设：用户名（就是主页上的 ID）、登录密码（≥8 位）、日记密码（≥4 位）。

## 数据库与图片

| 项 | 值 |
| --- | --- |
| 库 | `make_me_better`（utf8mb4） |
| 账号 | `mmb`（只对这个库有权限） |
| 密码 | 在 `application-local.yml`（不进版本库） |
| 表结构 | `db/migration/V1__init.sql`、`V2__profile_feed_archive.sql`，启动时自动执行 |
| 图片 | `data/uploads/avatars/`（头像）、`data/uploads/年/月/日/`（动态配图 + `_s` 缩略图） |

图片**不存数据库**：库里只存相对路径，文件落在磁盘。这样数据库小、备份快、图片加载也快。

**备份要两样都拿**：

```bash
mysqldump -u mmb -p --default-character-set=utf8mb4 make_me_better > 备份.sql
# 再把 data/uploads 整个目录拷走
```

**离线跑**（不想开 MySQL）：`mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev`，用本地 H2 文件库。

## 平时怎么用

- **发动态**：`/feed` 顶部写两句 → 「＋ 图片」选图 → 发布
- **写长文**：右上角 ✎ → 管理 → 写一篇（学习/日记），Markdown + 实时预览，Ctrl/⌘ + S 保存
- **记档案**：管理 → 档案 → 建档案 → 进去加条目，条目可以上移下移
- **改资料**：管理 → 设置 → 换头像、改 ID、改签名
- **改密码**：管理 → 设置，登录密码和日记密码各一个表单

## 自检

```bash
mvnw.cmd test
```

覆盖：建账号 → 改资料/头像 → 发带图动态 → 空动态被拒 → 建档案/加条目/排序 → 写学习 → 日记拦截/解锁 → 改 → 删 → 退出。

## 常见问题

**端口被占？** 改 `application.yml` 里的 `server.port`。

**连不上数据库？** 先 `Get-Service MySQL80`，再看 `application-local.yml` 在不在项目根目录。

**图片 404？** 看 `data/uploads` 里有没有文件；换过 `app.upload-dir` 的话，旧图路径就对不上了。

**页面样式是旧的？** `Ctrl + F5`。

**改完代码没生效？** 项目没有热重载，重启一次。
