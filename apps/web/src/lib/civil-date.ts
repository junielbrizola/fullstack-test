export interface IsoLocalDateParts {
  year: number;
  month: number;
  day: number;
}

const MIN_DATE: IsoLocalDateParts = { year: -4_713, month: 11, day: 24 };
const MAX_DATE: IsoLocalDateParts = { year: 5_874_897, month: 12, day: 31 };
const ISO_LOCAL_DATE = /^(-\d{4,9}|\+\d{5,9}|\d{4})-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;

function canonicalYear(year: number): string {
  if (year < 0) return '-' + String(Math.abs(year)).padStart(4, '0');
  if (year > 9_999) return '+' + String(year);
  return String(year).padStart(4, '0');
}

function compareParts(left: IsoLocalDateParts, right: IsoLocalDateParts): number {
  return left.year - right.year || left.month - right.month || left.day - right.day;
}

export function parseIsoLocalDate(value: unknown): IsoLocalDateParts | null {
  if (typeof value !== 'string') return null;
  const match = ISO_LOCAL_DATE.exec(value);
  if (!match || match[0] !== value) return null;

  const yearText = match[1];
  const monthText = match[2];
  const dayText = match[3];
  if (!yearText || !monthText || !dayText) return null;

  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  if (!Number.isSafeInteger(year) || canonicalYear(year) !== yearText) return null;

  const leapYear = year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  const daysInMonth = [31, leapYear ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (day > (daysInMonth[month - 1] ?? 0)) return null;

  const parsed = { year, month, day };
  if (compareParts(parsed, MIN_DATE) < 0 || compareParts(parsed, MAX_DATE) > 0) return null;
  return parsed;
}

export function isSupportedIsoLocalDate(value: unknown): value is string {
  return parseIsoLocalDate(value) !== null;
}

export function isNativeDateInputValue(value: string): boolean {
  const parsed = parseIsoLocalDate(value);
  return parsed !== null && parsed.year >= 1 && parsed.year <= 9_999 && /^\d{4}-/.test(value);
}

export function compareIsoLocalDates(left: string, right: string): number | null {
  const leftParts = parseIsoLocalDate(left);
  const rightParts = parseIsoLocalDate(right);
  return leftParts === null || rightParts === null ? null : compareParts(leftParts, rightParts);
}
