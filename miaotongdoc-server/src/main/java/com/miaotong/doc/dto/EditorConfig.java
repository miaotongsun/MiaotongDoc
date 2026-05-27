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
