package com.makemebetter.domain;

/** 一次计时的状态：跑着、跑完了、中途放弃 */
public enum SessionStatus {
    RUNNING,
    DONE,
    CANCELLED
}
