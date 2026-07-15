package com.miaotong.doc.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EditorConfig {

    private Document document;
    private String documentType;
    private EditorConfigData editorConfig;
    private String token;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Document {
        private String fileType;
        private String key;
        private String title;
        private String url;
        private Permissions permissions;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Permissions {
        private Boolean comment;
        private Boolean download;
        private Boolean edit;
        private Boolean print;
        private Boolean review;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EditorConfigData {
        private String callbackUrl;
        private UserInfo user;
        private String lang;
        private String mode;
        private Customization customization;
        private CoEditing coEditing;
        private Boolean canRequestRefreshFile;
        /**
         * MiaotongDoc v2.7.2：注入插件配置（key 是插件的 GUID 或 config.json 里的 name）
         * 注入 aiPluginSettings 字符串后，OnlyOffice 启动插件时
         *   window.Asc.plugin.info.aiPluginSettings 就有值了
         * 插件 JS 用它来初始化 AI.serverSettings（覆盖 localStorage 旧配置）
         */
        private java.util.Map<String, Plugins> plugins;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Plugins {
        /** 插件初始化参数（字符串，由插件自己 JSON.parse） */
        private String aiPluginSettings;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CoEditing {
        private String mode;
        private Boolean change;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserInfo {
        private String id;
        private String name;
        private String firstname;
        private String lastname;
        private String group;
        private String image;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Customization {
        private Boolean forcesave;
    }
}
