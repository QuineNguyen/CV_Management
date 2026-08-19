package com.training.cvmanagementbe.constant;

public final class ApiPath {

    public static final String DEPARTMENTS = "/departments";
    public static final String TREE = "/tree";
    public static final String BY_ID = "/{id}";
    public static final String REORDER_ROOTS = "/reorder";
    public static final String REORDER_BY_PARENT = "/{parentId}/reorder";

    private ApiPath() {}
}
