package com.makemebetter;

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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

        // 10.1 新建条目：进到一整页的写作界面
        mvc.perform(get("/admin/archives/" + archiveId + "/items/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("doc-title")))
                .andExpect(content().string(containsString("doc-text")));

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
                .andExpect(content().string(not(containsString("name=\"mood\""))));

        // 从管理进来还是能选
        mvc.perform(get("/admin/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("kind-picker")));

        // 14. 日记：没解锁被挡，解锁后能读
        mvc.perform(get("/diary").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diary/unlock"));

        mvc.perform(post("/diary/unlock").session(session).with(csrf()).param("password", "nope"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("不对")));

        mvc.perform(post("/diary/unlock").session(session).with(csrf()).param("password", "jot1234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/diary"));

        MvcResult diary = mvc.perform(post("/admin/entries").session(session).with(csrf())
                        .param("kind", "diary")
                        .param("title", "测试日记条目")
                        .param("bodyMd", "只有我能看。")
                        .param("entryDate", "2026-01-03"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String diaryLocation = diary.getResponse().getRedirectedUrl();
        assertNotNull(diaryLocation);
        String diaryId = diaryLocation.replaceAll("^/diary/(\\d+).*$", "$1");

        mvc.perform(get("/diary/" + diaryId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("测试日记条目")));

        // 15. 学习列表里不该出现日记和动态
        mvc.perform(get("/study").session(session))
                .andExpect(content().string(not(containsString("测试日记条目"))))
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

        // 17. 404（登录状态下）
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

    /** 造一张 8×8 的真 PNG，走 ImageIO 校验这一步 */
    private byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
