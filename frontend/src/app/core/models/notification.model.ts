export interface NotificationPageState {
    index: number;
    size: number;
    total: number;
}

// Large counts are capped so the bell keeps its size
const BADGE_MAX = 99;

export function badgeLabelOf(count: number): string {
    return count > BADGE_MAX ? `${BADGE_MAX}+` : String(count);
}