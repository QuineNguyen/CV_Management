package com.training.cvmanagementbe.enums.cvs;

// One change of a version against its predecessor, at (section, item, field) granularity. Used to generate a change log for a version.
public enum ChangeType {
    ADDED,
    MODIFIED,
    REMOVED
}
