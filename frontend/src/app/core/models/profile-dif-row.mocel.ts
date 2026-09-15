/*
 * One line of the current-versus-proposed comparison.
 *
 * `changed` is what the reviewer actually scans for: a field the requester did not touch still
 * has to appear or the table would silently omit context, but it must not read like a change.
 */
export interface ProfileDiffRow {
    label: string;
    current: string;
    requested: string;
    changed: boolean;
    isPhoto: boolean;
}