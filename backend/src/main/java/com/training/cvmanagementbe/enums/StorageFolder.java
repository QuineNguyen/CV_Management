package com.training.cvmanagementbe.enums;

// Object key prefixes in the bucket, so no folder name is spelled out at a call site.
public enum StorageFolder {

    AVATARS("avatars");

    private final String value;

    StorageFolder(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    // Builds a full object key: avatars/<name>
    public String keyOf(String objectName) {
        return value + "/" + objectName;
    }
}
