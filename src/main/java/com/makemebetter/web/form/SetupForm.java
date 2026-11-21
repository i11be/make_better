package com.makemebetter.web.form;

/** 首次设置账号的表单。 */
public class SetupForm {

    private String username = "me";
    private String password = "";
    private String diary = "";
    private String diary2 = "";

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDiary() {
        return diary;
    }

    public void setDiary(String diary) {
        this.diary = diary;
    }

    public String getDiary2() {
        return diary2;
    }

    public void setDiary2(String diary2) {
        this.diary2 = diary2;
    }
}
