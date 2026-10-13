// "1 day", "3 days"
export function pluralize(count: number, unit: string): string {
    return `${count} ${unit}${count === 1 ? '' : 's'}`;
}