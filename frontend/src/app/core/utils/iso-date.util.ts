// yyyy-MM-dd of the local calendar day; toISOString() would shift it to the day before in UTC+7
export function toIsoDate(date: Date | null): string | null {
    if (!date) {
        return null;
    }
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${date.getFullYear()}-${month}-${day}`;
}

// Midnight today, for the [min] bound of a datepicker
export function startOfToday(): Date {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return today;
}