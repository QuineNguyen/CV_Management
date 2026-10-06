package com.training.cvmanagementbe.enums.cvs;

/*
 * One change of a version against its predecessor, at (section, item, field) granularity. Used to generate a change log for a version.
 * - ADDED / MODIFIED / REMOVED: Stored in change_log_entries and returned by the diff.
 * - UNCHANGED: Read-time diff only, so the viewer can show unchanged content next to the changes.
 * ChangeLogGenerator never produces it and the CHECK on change_log_entries.change_type would reject it.
 */
public enum ChangeType {
    ADDED,
    MODIFIED,
    REMOVED,
    UNCHANGED
}
