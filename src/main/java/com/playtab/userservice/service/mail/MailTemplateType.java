package com.playtab.userservice.service.mail;

public enum MailTemplateType {
    EMAIL_VERIFICATION("mail/email-verification"),
    WELCOME("mail/welcome");

    private final String templateName;

    MailTemplateType(String templateName) {
        this.templateName = templateName;
    }

    public String templateName() {
        return templateName;
    }
}