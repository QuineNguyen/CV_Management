package com.training.cvmanagementbe.enums;

/*
 * The only three ways a CV version can be created.
 *
 * - DIRECT_EDIT is valid only when the editor is the CV owner. There is no path for
 * Admin/HR to write a version onto someone else's CV.
 */
public enum VersionSource {
    APPROVAL,
    DIRECT_EDIT,
    ROLLBACK
}
