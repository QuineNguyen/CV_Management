package com.training.cvmanagementbe.record;

import java.util.UUID;

/*
 * Common shape of every entry inside a REPEATED section.
 *
 * - Sealed interface rather than an abstract class: the entries are records and records cannot
 * extend a class. The for accessors below are the contract every entry must expose.
 *
 * - itemId is the anchor for inline comments, the diff key between two versions and the
 * matching key used by master/localization sync. It is generated once and never charges: shared
 * across languages of the same profile, never shared across two profiles.
 */
public sealed interface RepeatedEntry
        permits SkillEntry, ExperienceEntry, EducationEntry, CertificationEntry,
                ProjectEntry, LanguageEntry, AdditionalInfoEntry {

    // Stable UUID string, immutable once assigned.
    String itemId();

    // Position inside its section; supports drag-and-drop reordering.
    int displayOrder();

    // Placeholder created by structure sync and not translated yet.
    boolean untranslated();

    // The counterpart entry was removed in the master; flagged, never auto-deleted.
    boolean deletedInMaster();

    // Fills a missing id on creation; an existing id is passed through untouched.
    static String ensureItemId(String itemId) {
        return (itemId == null || itemId.isBlank()) ? UUID.randomUUID().toString(): itemId;
    }
}
