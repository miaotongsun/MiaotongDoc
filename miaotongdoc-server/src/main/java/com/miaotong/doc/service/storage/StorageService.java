package com.miaotong.doc.service.storage;

public interface StorageService {

    String store(String objectKey, byte[] content);

    byte[] load(String objectKey);

    void delete(String objectKey);

    boolean exists(String objectKey);
}
