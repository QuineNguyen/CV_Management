package com.training.cvmanagementbe.record.cvs;

import com.training.cvmanagementbe.enums.cvs.CvSectionKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * What changed in a master's structure between two published versions, per REPEATED section.
 *
 * - Structure is the list of item_ids in array order; field values never appear here.
 * - added / removed hold only sections that actually changed; masterOrder holds every section,
 * because placing a new entry needs its neighbours even when nothing else moved.
 */
public record MasterStructureDiff(
        // item_ids in the new version but not the previous one, in master order
        Map<CvSectionKey, List<String>> added,
        // item_ids in the previous version that are gone from the new one
        Map<CvSectionKey, Set<String>> removed,
        // Sections whose shared ids changed relative order
        Set<CvSectionKey> reordered,
        // item_ids of the new version, in order, for every repeated section
        Map<CvSectionKey, List<String>> masterOrder
) {

    // A master edit that only touched field values: nothing to carry over.
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty() && reordered.isEmpty();
    }

    public List<String> addedIn(CvSectionKey sectionKey) {
        return added.getOrDefault(sectionKey, List.of());
    }

    public Set<String> removedIn(CvSectionKey sectionKey) {
        return removed.getOrDefault(sectionKey, Set.of());
    }

    public List<String> masterOrderOf(CvSectionKey sectionKey) {
        return masterOrder.getOrDefault(sectionKey, List.of());
    }
}
