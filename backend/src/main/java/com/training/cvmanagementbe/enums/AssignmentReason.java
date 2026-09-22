package com.training.cvmanagementbe.enums;

/*
 * The sentences written into approval_assignments.reason and approval_decisions.reason
 * when the system picks an approver by itself.
 *
 * - Why this is an enum and not a string literal at each branch of the resolver: the reason column
 * is the only record of why a past round went to a given person. team_members keeps no history, so
 * once the employee changes team the wording here is the whole audit trail. Keeping every branch's
 * wording in one place means the vocabulary stays consistent across rounds and a change to it is one
 * diff rather than eight.
 * - Each constant is a formatted template; the argument list is documented per constant because the
 * compiler cannot check it.
 */
public enum AssignmentReason {

    // args: linked team name, profile name
    LINKED_TEAM_LEAD("Tech Lead of the team linked to this profile: '%s' (profile '%s')"),

    // args: primary team name
    PRIMARY_TEAM_LEAD("Tech Lead of the employee's primary team: '%s'"),

    // args: (none)
    SOLE_ELIGIBLE_LEAD("Only eligible tech lead across the teams the employee belongs to"),

    // args: candidate count, open assignment count of the chosen person
    LEAST_LOADED_LEAD("Fewest open assignments among %d eligible tech leads (%d open)"),

    // args: tied candidate count, open assignment count
    ROUND_ROBIN_LEAD("Round robin among %d tech leads tied on %d open assignments; idle the longest"),

    // args: submitted id
    LEVEL_1_SKIPPED_SUBMITTER("Skipped: the only eligible tech lead is the submitter (userId=%s)"),

    // args: linked team name, profile name
    /*
     * Separate constant rather than a reworded version of the one above, because the two skips
     * answer different questions. This one says the CV owner is the right technical reviewer for
     * this profile; the one above says no other reviewer exists at all. Reporting on "how often is
     * technical review bypassed" has to be able to tell them apart - merging the wording would make
     * that permanently unrecoverable, since the reason text is the only record either way.
     */
    LEVEL_1_SKIPPED_PROFILE_TEAM_LEAD("Skipped: the submitter is the Tech Lead of team '%s', " +
            "the team this profile ('%s') is linked to"),

    // args: open assignment count of the chosen person, candidate count
    LEAST_LOADED_HR("HR with the fewest open assignments (%d open, %d eligible)"),

    // args: tied candidate count, open assignment count
    ROUND_ROBIN_HR("Round robin among %d HR tied on %d open assignments; idle the longest"),

    // args: open assignment count of the chosen person, candidate count
    ADMIN_FALLBACK("Admin fallback: no active HR available; least loaded admin (%d open, %d eligible)"),

    // args: tied candidate count, open assignment count
    STICKY_RESUBMIT_LEAD("Same tech lead as previous round; resubmitted after rejection"),

    // args: tied candidate count, open assignment count
    STICKY_RESUBMIT_HR("Same HR as previous round; resubmitted after rejection");

    private final String template;

    AssignmentReason(String template) {
        this.template = template;
    }

    public String format(Object... args) {
        return template.formatted(args);
    }
}
