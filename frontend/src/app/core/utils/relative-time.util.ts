const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;
const WEEK_MS = 7 * DAY_MS;

// Short age label for feed items; older than a week falls back to the date.
export function relativeTimeOf(iso: string, now: number = Date.now()): string {
    const elapsed = Math.max(0, now - new Date(iso).getTime());

    if (elapsed < MINUTE_MS) {
        return 'Just now';
    }
    if (elapsed < HOUR_MS) {
        return `${Math.floor(elapsed / MINUTE_MS)} min ago`;
    }
    if (elapsed < DAY_MS) {
        return `${Math.floor(elapsed / HOUR_MS)} h ago`;
    }
    if (elapsed < WEEK_MS) {
        const days = Math.floor(elapsed / DAY_MS);
        return days === 1 ? 'Yesterday' : `${days} days ago`;
    }
    return new Date(iso).toLocaleDateString('en-GB');
}