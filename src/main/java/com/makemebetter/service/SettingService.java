package com.makemebetter.service;

import com.makemebetter.domain.Setting;
import com.makemebetter.repository.SettingRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** 站点文案的读写。默认值写在这里，数据库里没存过就用默认值。 */
@Service
public class SettingService implements ApplicationRunner {

    public static final String SITE_TITLE = "site_title";
    public static final String SITE_TAGLINE = "site_tagline";
    public static final String OWNER_NAME = "owner_name";
    public static final String ABOUT_MD = "about_md";
    public static final String NOW_MD = "now_md";

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(SITE_TITLE, "make me better");
        DEFAULTS.put(SITE_TAGLINE, "把自己重新养一遍，一天一天来");
        DEFAULTS.put(OWNER_NAME, "我");
        DEFAULTS.put(ABOUT_MD, """
                你好，这里是我自己用的一块地方。

                我在这里记学习、记日常，也写日记。
                不是为了给谁看，是为了过一段时间回头，能看见自己是怎么慢慢变好的。
                """);
        DEFAULTS.put(NOW_MD, "现在在学：**Java**、**英语**。\n最近在做：把这个小站一点点搭起来。");
    }

    private final SettingRepository repository;

    public SettingService(SettingRepository repository) {
        this.repository = repository;
    }

    /** 启动时补齐缺失的默认值（Flyway 建完表之后才跑）。 */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        DEFAULTS.forEach((key, value) -> {
            if (repository.findById(key).isEmpty()) {
                repository.save(new Setting(key, value));
            }
        });
    }

    public String get(String key) {
        return repository.findById(key)
                .map(Setting::getValue)
                .orElseGet(() -> DEFAULTS.getOrDefault(key, ""));
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> merged = new LinkedHashMap<>(DEFAULTS);
        repository.findAll().forEach(setting -> merged.put(setting.getKey(), setting.getValue()));
        return merged;
    }

    @Transactional
    public void set(String key, String value) {
        if (!DEFAULTS.containsKey(key)) {
            throw new IllegalArgumentException("未知的设置项：" + key);
        }
        String safe = value == null ? "" : value.strip();
        repository.save(new Setting(key, safe));
    }

    @Transactional
    public void setAll(Map<String, String> values) {
        values.forEach(this::set);
    }
}
