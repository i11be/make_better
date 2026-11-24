package com.makemebetter.config;

import com.makemebetter.web.DiaryGateInterceptor;
import com.makemebetter.web.SetupGateInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SetupGateInterceptor setupGate;
    private final DiaryGateInterceptor diaryGate;

    public WebConfig(SetupGateInterceptor setupGate, DiaryGateInterceptor diaryGate) {
        this.setupGate = setupGate;
        this.diaryGate = diaryGate;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 还没建账号：先去 /setup
        registry.addInterceptor(setupGate)
                .addPathPatterns("/**")
                .excludePathPatterns("/setup", "/error", "/css/**", "/js/**", "/favicon.svg");

        // 日记：进出都要过这道锁（进要密码，离开就重新锁上）
        registry.addInterceptor(diaryGate)
                .addPathPatterns("/**")
                .excludePathPatterns("/diary/unlock",
                        "/css/**", "/js/**", "/img/**", "/files/**", "/favicon.svg");
    }
}
