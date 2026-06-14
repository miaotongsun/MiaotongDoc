package com.miaotong.doc.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Document(indexName = "documents")
public class DocumentIndex {

    @Id
    private String id;

    @Field(type = FieldType.Long)
    private Long documentId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;

    @Field(type = FieldType.Keyword)
    private String docType;

    @Field(type = FieldType.Keyword)
    private String fileType;

    @Field(type = FieldType.Long)
    private Long ownerUserId;

    @Field(type = FieldType.Keyword)
    private String ownerName;

    @Field(type = FieldType.Long)
    private Long departmentId;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime createdAt;

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    private LocalDateTime updatedAt;
}
