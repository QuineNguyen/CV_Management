package com.training.cvmanagementbe.scheduler;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// Batch lookups: One query per table instead of one per row
final class ReminderLookups {

    private ReminderLookups() {

    }

    static <T> Set<UUID> ids(Collection<T> rows, Function<T, UUID> extractor) {
        return rows.stream().map(extractor).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    static <T> Map<UUID, T> indexById(Collection<T> rows, Function<T, UUID> idOf) {
        return rows.stream().collect(Collectors.toMap(idOf, Function.identity()));
    }
}
