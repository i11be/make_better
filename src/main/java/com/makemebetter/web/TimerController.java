package com.makemebetter.web;

import com.makemebetter.domain.StudySession;
import com.makemebetter.service.StudyTimerService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 学习计时：开始、时间到、放弃。 */
@Controller
@RequestMapping("/timer")
public class TimerController {

    /** 最短 5 分钟，最长 3 小时，防止手滑输入 99999 */
    private static final int MIN_MINUTES = 5;
    private static final int MAX_MINUTES = 180;

    private final StudyTimerService timer;

    public TimerController(StudyTimerService timer) {
        this.timer = timer;
    }

    @PostMapping("/start")
    public String start(@RequestParam(name = "minutes", required = false) Integer minutes,
                        @RequestParam(name = "customMinutes", required = false) String customMinutes,
                        RedirectAttributes redirect) {
        int planned = parseMinutes(customMinutes, minutes == null ? 30 : minutes);
        planned = Math.max(MIN_MINUTES, Math.min(MAX_MINUTES, planned));
        StudySession session = timer.start(planned);
        redirect.addFlashAttribute("timerMessage", "开始计时：" + session.getPlannedMinutes() + " 分钟");
        return "redirect:/profile";
    }

    /** 输入框优先；填了不是数字就当没填 */
    private int parseMinutes(String custom, int fallback) {
        if (custom == null || custom.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(custom.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 倒计时归零时由页面调一次；重复调用无副作用 */
    @PostMapping("/finish")
    @ResponseBody
    public ResponseEntity<Void> finish(@RequestParam(name = "id") Long id) {
        timer.finish(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/cancel")
    public String cancel(@RequestParam(name = "id") Long id, RedirectAttributes redirect) {
        timer.cancel(id);
        redirect.addFlashAttribute("timerMessage", "这段放弃了，不计时长");
        return "redirect:/profile";
    }
}
