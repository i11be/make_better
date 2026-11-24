package com.makemebetter;

import com.makemebetter.config.RequestLogFilter;
import com.makemebetter.domain.Entry;
import com.makemebetter.domain.EntryKind;
import com.makemebetter.domain.StudySession;
import com.makemebetter.service.EntryService;
import com.makemebetter.service.StudyTimerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 自检：建账号 → 登录态 → 写学习 → 发动态（带图）→ 建档案（条目、排序）
 * → 日记被挡 → 解锁 → 改 → 删 → 退出。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RequestLogFilter requestLogFilter;

    @Autowired
    private EntryService entryService;

    @Autowired
    private StudyTimerService timerService;

    @Test
    void 请求日志过滤器默认开着() {
        assertNotNull(requestLogFilter, "app.request-log=true 时应该注册这个过滤器");
    }

    @Test
    void 走一遍完整流程() throws Exception {
        // 1. 整站都在门后：没登录，连首页都进不去
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mvc.perform(get("/feed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        // 2. 还没有账号时，登录页自己把人送去 /setup
        mvc.perform(get("/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/setup"));

        mvc.perform(get("/setup"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("先设置一下")));

        // 3. 建账号（建完自动登录）
        MvcResult setup = mvc.perform(post("/setup").with(csrf())
                        .param("username", "tester")
                        .param("password", "test12345")
                        .param("diary", "jot1234")
                        .param("diary2", "jot1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?ok=welcome"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) setup.getRequest().getSession(false);
        assertNotNull(session, "建账号后应该已经登录");

        // 4. 首页只剩那棵世界树：五个入口挂在上面，正文不该再出现
        mvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tree-node--home")))
                .andExpect(content().string(containsString("tree-node--diary")))
                .andExpect(content().string(not(containsString("为什么要有这个地方"))));

        // 4.1 树上的「主页」是头像、ID、签名
        mvc.perform(get("/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tester")));

        // 5. 改资料：ID + 签名 + 头像
        mvc.perform(multipart("/admin/profile").file(avatar()).with(csrf()).session(session)
                        .param("username", "小羊")
                        .param("bio", "把日子过明白"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings?ok=profile"));

        mvc.perform(get("/profile").session(session))
                .andExpect(content().string(containsString("小羊")))
                .andExpect(content().string(containsString("把日子过明白")))
                .andExpect(content().string(containsString("/files/avatars/")));

        // 6. 发一条带图的动态
        mvc.perform(multipart("/feed").file(photo("a.png")).file(photo("b.png"))
                        .with(csrf()).session(session)
                        .param("body", "今天去河边走了走。"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feed?ok=posted"));

        MvcResult feedPage = mvc.perform(get("/feed").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("今天去河边走了走")))
                .andExpect(content().string(containsString("/files/")))
                .andExpect(content().string(containsString("data-lightbox")))
                .andReturn();

        // 树上那个「动态」节点的计数跟着走
        mvc.perform(get("/").session(session))
                .andExpect(content().string(containsString("2 条")));

        // 7. 图片能真的取出来
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("data-full=\"([^\"]+)\"")
                .matcher(feedPage.getResponse().getContentAsString());
        assertTrue(matcher.find(), "页面里应该有图片地址");
        String imageUrl = matcher.group(1);
        mvc.perform(get(imageUrl).session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png"));

        // 8. 空动态不给发
        mvc.perform(multipart("/feed").with(csrf()).session(session).param("body", "  "))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("写点什么")));

        // 9. 建档案
        MvcResult archive = mvc.perform(post("/admin/archives").with(csrf()).session(session)
                        .param("title", "原神")
                        .param("summary", "主线剧情"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String archiveUrl = archive.getResponse().getRedirectedUrl();
        assertNotNull(archiveUrl);
        assertTrue(archiveUrl.matches("/admin/archives/\\d+"), archiveUrl);
        String archiveId = archiveUrl.replaceAll("^/admin/archives/(\\d+)$", "$1");

        // 10. 往里加两条条目
        MvcResult first = mvc.perform(post("/admin/archives/" + archiveId + "/items").with(csrf()).session(session)
                        .param("title", "序章")
                        .param("bodyMd", "旅行者**醒来**的地方。"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String firstUrl = first.getResponse().getRedirectedUrl();
        assertNotNull(firstUrl);
        String firstItemId = firstUrl.replaceAll(".*/items/(\\d+).*$", "$1");

        // 10.1 新建条目：进到一整页的写作界面；返回走前台档案页，不回后台
        mvc.perform(get("/admin/archives/" + archiveId + "/items/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("doc-title")))
                .andExpect(content().string(containsString("doc-text")))
                .andExpect(content().string(containsString("href=\"/archive/" + archiveId + "\"")));

        // 10.2 保存后留在编辑器里（不跳走）
        mvc.perform(post("/admin/archives/" + archiveId + "/items/" + firstItemId).with(csrf()).session(session)
                        .param("title", "序章")
                        .param("bodyMd", "旅行者**醒来**的地方。"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/archives/" + archiveId + "/items/" + firstItemId + "?ok=saved"));

        // 10.3 条目里能传图
        mvc.perform(multipart("/admin/archives/" + archiveId + "/images").file(upload())
                        .with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/files/archives/")));

        mvc.perform(post("/admin/archives/" + archiveId + "/items").with(csrf()).session(session)
                        .param("title", "第一章")
                        .param("bodyMd", "风起地。"))
                .andExpect(status().is3xxRedirection());

        // 11. 前台档案：目录 + 正文（Markdown 渲染）
        mvc.perform(get("/archive").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("原神")))
                .andExpect(content().string(containsString("2 条")));

        mvc.perform(get("/archive/" + archiveId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("序章")))
                .andExpect(content().string(containsString("第一章")))
                .andExpect(content().string(containsString("<strong>醒来</strong>")));

        // 12. 排序：把第一条往下移
        mvc.perform(post("/admin/archives/" + archiveId + "/items/" + firstItemId + "/move")
                        .with(csrf()).session(session)
                        .param("direction", "down"))
                .andExpect(status().is3xxRedirection());

        // 12.1 相册：上传到待整理 → 归类到新图集 → 图集里能看到 → 进回收站
        mvc.perform(multipart("/album/upload").file(albumPhoto()).with(csrf()).session(session)
                        .param("album", "_inbox"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/album"));

        MvcResult albumPage = mvc.perform(get("/album").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("待整理")))
                // 缩略图要能被灯箱接管，否则点了会跳去原图
                .andExpect(content().string(containsString("data-lightbox")))
                .andReturn();
        java.util.regex.Matcher thumb = java.util.regex.Pattern
                .compile("/album/thumb/_inbox/([^\"]+)")
                .matcher(albumPage.getResponse().getContentAsString());
        assertTrue(thumb.find(), "待整理里应该有一张图");
        String inboxPhoto = thumb.group(1);

        String albumName = "测试图集" + (System.currentTimeMillis() % 100000);
        mvc.perform(post("/album/move").with(csrf()).session(session)
                        .param("from", "_inbox")
                        .param("names", inboxPhoto)
                        .param("newAlbum", albumName))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/album").session(session))
                .andExpect(content().string(containsString(albumName)));

        MvcResult inAlbum = mvc.perform(get("/album/{name}", albumName).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/album/thumb/")))
                .andReturn();
        java.util.regex.Matcher inAlbumThumb = java.util.regex.Pattern
                .compile("/album/thumb/[^/]+/([^\"]+)")
                .matcher(inAlbum.getResponse().getContentAsString());
        assertTrue(inAlbumThumb.find(), "图集里应该有这张图");

        mvc.perform(post("/album/trash").with(csrf()).session(session)
                        .param("from", albumName)
                        .param("names", inAlbumThumb.group(1)))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/album/{name}", albumName).session(session))
                .andExpect(content().string(containsString("这个图集还空着")));

        // 图集名不合法要被挡住
        mvc.perform(post("/album/move").with(csrf()).session(session)
                        .param("from", "_inbox")
                        .param("names", inboxPhoto)
                        .param("newAlbum", "../etc"))
                .andExpect(status().is3xxRedirection());

        // 13. 写一条学习
        MvcResult created = mvc.perform(post("/admin/entries").session(session).with(csrf())
                        .param("kind", "study")
                        .param("title", "测试学习条目")
                        .param("bodyMd", "这是 **粗体** 和 `代码`\n第二行")
                        .param("entryDate", "2026-01-02")
                        .param("mood", "专注")
                        .param("category", "bagu")
                        .param("tags", "二分"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String location = created.getResponse().getRedirectedUrl();
        assertNotNull(location);
        assertTrue(location.matches("/study/\\d+\\?ok=saved"), "跳转地址不对：" + location);
        String studyId = location.replaceAll("^/study/(\\d+).*$", "$1");

        mvc.perform(get("/study/" + studyId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<strong>粗体</strong>")))
                .andExpect(content().string(containsString("<code>代码</code>")))
                // 单个换行要真的换行
                .andExpect(content().string(containsString("<br />")))
                .andExpect(content().string(containsString("第二行")))
                .andExpect(content().string(containsString("八股")))
                .andExpect(content().string(containsString("#二分")))
                // 「改这篇」的链接得是真实 id，不能是字面量 entry.id
                .andExpect(content().string(containsString("/admin/entries/" + studyId + "/edit")))
                // 学习里没有心情
                .andExpect(content().string(not(containsString("专注"))));

        // 13.2 长正文不许被截断（TEXT 是 64KB，这里写 90KB）
        String longBody = "长".repeat(30000) + "尾巴标记";
        MvcResult longOne = mvc.perform(post("/admin/entries").session(session).with(csrf())
                        .param("kind", "study")
                        .param("title", "长文测试")
                        .param("bodyMd", longBody))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String longLocation = longOne.getResponse().getRedirectedUrl();
        assertNotNull(longLocation);
        String longId = longLocation.replaceAll("^/study/(\\d+).*$", "$1");

        mvc.perform(get("/study/" + longId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("尾巴标记")));

        // 13.1 学习列表：分类筛选、标签分组、写一篇锁死类型
        mvc.perform(get("/study").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("测试学习条目")))
                .andExpect(content().string(containsString("按标签")));

        mvc.perform(get("/study?cat=bagu").session(session))
                .andExpect(content().string(containsString("测试学习条目")));

        mvc.perform(get("/study?cat=algo").session(session))
                .andExpect(content().string(not(containsString("测试学习条目"))));

        mvc.perform(get("/study?sort=tag").session(session))
                .andExpect(content().string(containsString("#二分")))
                .andExpect(content().string(containsString("按标签")));

        mvc.perform(get("/study?tag=二分").session(session))
                .andExpect(content().string(containsString("测试学习条目")));

        // 从学习进来只能写学习：没有三选一，只有分类，也没有心情那一格
        mvc.perform(get("/admin/new?kind=study").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("kind-picker"))))
                .andExpect(content().string(containsString("分类")))
                .andExpect(content().string(not(containsString("name=\"mood\""))))
                // 返回回学习列表，不跳后台
                .andExpect(content().string(containsString("href=\"/study\"")));

        // 从管理进来还是能选，返回回管理
        mvc.perform(get("/admin/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("kind-picker")))
                .andExpect(content().string(containsString("href=\"/admin\"")));

        // 14. 日记：没解锁被挡，解锁后能写
        mvc.perform(get("/diary").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diary/unlock"));

        mvc.perform(post("/diary/unlock").session(session).with(csrf()).param("password", "nope"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("不对")));

        mvc.perform(post("/diary/unlock").session(session).with(csrf()).param("password", "jot1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diary"));

        // 14.1 写今天的日记：日期自动是今天，标题也是日期
        MvcResult diary = mvc.perform(post("/diary/new").session(session).with(csrf())
                        .param("body", "只有我能看。"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String diaryLocation = diary.getResponse().getRedirectedUrl();
        assertNotNull(diaryLocation);
        assertTrue(diaryLocation.matches("/diary/\\d+\\?ok=saved"), diaryLocation);
        String diaryId = diaryLocation.replaceAll("^/diary/(\\d+).*$", "$1");

        mvc.perform(get("/diary/" + diaryId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("只有我能看")))
                .andExpect(content().string(containsString("改今天的")));

        // 14.2 一天一篇：再建一次跳回同一篇
        mvc.perform(post("/diary/new").session(session).with(csrf()).param("body", "第二篇"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diary/" + diaryId + "?ok=exists"));

        // 14.3 当天可以改
        mvc.perform(post("/diary/" + diaryId + "/edit").session(session).with(csrf())
                        .param("body", "改过的日记。"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diary/" + diaryId + "?ok=saved"));

        mvc.perform(get("/diary/" + diaryId).session(session))
                .andExpect(content().string(containsString("改过的日记")));

        // 14.4 列表只按日期排，今天的带「今天」标记
        mvc.perform(get("/diary").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("今天")));

        // 14.5 过了当天：不能改、不能删，详情页也不给按钮
        Entry oldDiary = entryService.create(EntryKind.DIARY, "旧日记", "很久以前写的。", "", "", "",
                LocalDate.now().minusDays(3));
        mvc.perform(get("/diary/" + oldDiary.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("只能看")))
                .andExpect(content().string(not(containsString("改今天的"))));

        mvc.perform(get("/diary/" + oldDiary.getId() + "/edit").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(post("/diary/" + oldDiary.getId() + "/edit").session(session).with(csrf())
                        .param("body", "想改"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/diary/" + oldDiary.getId() + "/delete").session(session).with(csrf()))
                .andExpect(status().isForbidden());
        // 管理端也不能碰日记
        mvc.perform(post("/admin/entries/" + oldDiary.getId() + "/delete").session(session).with(csrf()))
                .andExpect(status().isForbidden());

        // 14.6 离开日记区就重新锁上：看过别的页面，再进日记要密码
        mvc.perform(get("/study").session(session)).andExpect(status().isOk());
        mvc.perform(get("/diary").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diary/unlock"));

        // 15. 学习列表里不该出现日记和动态
        mvc.perform(get("/study").session(session))
                .andExpect(content().string(not(containsString("改过的日记"))))
                .andExpect(content().string(not(containsString("今天去河边走了走"))));

        // 16. 改一篇、删一篇
        mvc.perform(post("/admin/entries/" + studyId).session(session).with(csrf())
                        .param("kind", "study")
                        .param("title", "改成新的标题")
                        .param("bodyMd", "改过的正文。")
                        .param("entryDate", "2026-01-04"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/study/" + studyId + "?ok=saved"));

        mvc.perform(get("/study/" + studyId).session(session))
                .andExpect(content().string(containsString("改成新的标题")));

        mvc.perform(post("/admin/entries/" + studyId + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin?ok=deleted"));

        // 17. 学习计时：开始 → 时间到 → 计入统计；放弃的不算
        mvc.perform(post("/timer/start").session(session).with(csrf()).param("customMinutes", "45"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        StudySession running = timerService.current().orElseThrow();
        assertTrue(running.getPlannedMinutes() == 45, "自定义分钟数没生效");

        mvc.perform(get("/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("seg-clock")))
                .andExpect(content().string(containsString("data-remaining=")))
                .andExpect(content().string(containsString("45 分钟")));

        // 到点（页面会调这个接口），再调一次也不会重复记账
        mvc.perform(post("/timer/finish").session(session).with(csrf())
                        .param("id", String.valueOf(running.getId())))
                .andExpect(status().isNoContent());
        mvc.perform(post("/timer/finish").session(session).with(csrf())
                        .param("id", String.valueOf(running.getId())))
                .andExpect(status().isNoContent());

        assertEquals(45 * 60L, timerService.stats().today());

        // 放弃的那段不算
        StudySession abandoned = timerService.start(30);
        mvc.perform(post("/timer/cancel").session(session).with(csrf())
                        .param("id", String.valueOf(abandoned.getId())))
                .andExpect(status().is3xxRedirection());
        assertEquals(45 * 60L, timerService.stats().today());

        // 18. 404（登录状态下）
        mvc.perform(get("/no-such-page").session(session)).andExpect(status().isNotFound());

        // 18. 退出之后整站都要重新登录，不只是后台
        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mvc.perform(get("/study"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        // 19. 重新登录：对了回世界树，并弹一句「身份核验成功」
        MvcResult back = mvc.perform(post("/login").with(csrf())
                        .param("username", "小羊")
                        .param("password", "test12345"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?ok=login"))
                .andReturn();

        MockHttpSession backSession = (MockHttpSession) back.getRequest().getSession(false);
        assertNotNull(backSession, "登录成功应该拿到会话");
        mvc.perform(get("/?ok=login").session(backSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("身份核验成功")));

        // 20. 密码不对，回登录页
        mvc.perform(post("/login").with(csrf())
                        .param("username", "小羊")
                        .param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        // 21. 登录页平时只有海报和芯片，密码错了卡片才是展开的
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("gate-chip")))
                .andExpect(content().string(not(containsString("checked=\"checked\""))));

        mvc.perform(get("/login?error"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("checked=\"checked\"")))
                .andExpect(content().string(containsString("Wrong username or password")));
    }

    private MockMultipartFile avatar() throws IOException {
        return new MockMultipartFile("avatar", "me.png", "image/png", png());
    }

    private MockMultipartFile photo(String name) throws IOException {
        return new MockMultipartFile("photos", name, "image/png", png());
    }

    /** 档案条目里插的图，字段名是 file */
    private MockMultipartFile upload() throws IOException {
        return new MockMultipartFile("file", "page.png", "image/png", png());
    }

    /** 相册上传的图 */
    private MockMultipartFile albumPhoto() throws IOException {
        return new MockMultipartFile("file", "album.png", "image/png", png());
    }

    /** 造一张 8×8 的真 PNG，走 ImageIO 校验这一步 */
    private byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
