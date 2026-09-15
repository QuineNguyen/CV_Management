import { Injectable } from '@angular/core';
import { NativeDateAdapter, MatDateFormats, MAT_DATE_FORMATS, DateAdapter } from '@angular/material/core';

export const DD_MM_YYYY_FORMATS: MatDateFormats = {
  parse: {
    dateInput: 'input',
  },
  display: {
    dateInput: 'input',
    monthYearLabel: { year: 'numeric', month: 'short' },
    dateA11yLabel: { year: 'numeric', month: 'long', day: 'numeric' },
    monthYearA11yLabel: { year: 'numeric', month: 'long' },
  },
};

@Injectable()
export class CustomDateAdapter extends NativeDateAdapter {
  override parse(value: any): Date | null {
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (!trimmed) {
        return null;
      }
      const parts = trimmed.split(/[\/\-\.]/);
      if (parts.length === 3) {
        const day = parseInt(parts[0], 10);
        const month = parseInt(parts[1], 10);
        const year = parseInt(parts[2], 10);
        if (
          !isNaN(day) &&
          !isNaN(month) &&
          !isNaN(year) &&
          year >= 1000 &&
          year <= 9999 &&
          month >= 1 &&
          month <= 12 &&
          day >= 1 &&
          day <= 31
        ) {
          const d = new Date(year, month - 1, day);
          if (d.getFullYear() === year && d.getMonth() === month - 1 && d.getDate() === day) {
            return d;
          }
        }
        return this.invalid();
      }
    }
    return super.parse(value);
  }

  override format(date: Date, displayFormat: any): string {
    if (!this.isValid(date)) {
      throw Error('CustomDateAdapter: Cannot format invalid date.');
    }
    if (displayFormat === 'input') {
      const day = String(date.getDate()).padStart(2, '0');
      const month = String(date.getMonth() + 1).padStart(2, '0');
      const year = date.getFullYear();
      return `${day}/${month}/${year}`;
    }
    return super.format(date, displayFormat);
  }
}
