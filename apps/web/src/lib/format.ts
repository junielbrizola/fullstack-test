import { PUBLIC_BUSINESS_TIME_ZONE } from '$app/env/public';
import type { Money, Principal, WithdrawalResult } from './api/types';
import { parseIsoLocalDate } from './civil-date';

const BUSINESS_TIME_ZONE = PUBLIC_BUSINESS_TIME_ZONE ?? 'America/Sao_Paulo';

const taxRateLabels: Record<WithdrawalResult['taxRate'], string> = {
  '0.225': '22,5%',
  '0.185': '18,5%',
  '0.15': '15%'
};

export function formatMoney(value: Money | Principal): string {
  const parts = value.split('.');
  const whole = parts[0] ?? '0';
  const fraction = (parts[1] ?? '').padEnd(2, '0');
  return formatMoneyCents(BigInt(whole) * 100n + BigInt(fraction.slice(0, 2)));
}

export function sumMoneyCents(values: readonly (Money | Principal)[]): bigint {
  return values.reduce((sum, value) => {
    const [whole = '0', fraction = ''] = value.split('.');
    return sum + BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0').slice(0, 2));
  }, 0n);
}

export function formatMoneyCents(cents: bigint): string {
  const whole = cents / 100n;
  const fraction = String(cents % 100n).padStart(2, '0');
  const groupedWhole = new Intl.NumberFormat('pt-BR', {
    maximumFractionDigits: 0
  }).format(whole);

  return 'R$\u00a0' + groupedWhole + ',' + fraction;
}

export function formatDate(value: string): string {
  const date = parseIsoLocalDate(value);
  if (date === null) return value;

  const yearOfEra = date.year <= 0 ? 1 - date.year : date.year;
  const yearText = String(yearOfEra).padStart(4, '0');
  const era = date.year <= 0 ? ' a.C.' : date.year > 9_999 ? ' d.C.' : '';
  return String(date.day).padStart(2, '0') + '/'
    + String(date.month).padStart(2, '0') + '/'
    + yearText + era;
}

export function formatTaxRate(value: WithdrawalResult['taxRate']): string {
  return taxRateLabels[value];
}

export function businessToday(): string {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: BUSINESS_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(new Date());
  const datePart = (name: string): string =>
    parts.find((part) => part.type === name)?.value ?? '';

  return datePart('year') + '-' + datePart('month') + '-' + datePart('day');
}

export function normalizeMoneyInput(value: string): Principal | null {
  const trimmed = value.trim();
  if (/^\d{1,17},\d{1,2}$/.test(trimmed)) return trimmed.replace(',', '.');
  if (/^\d{1,17}(?:\.\d{1,2})?$/.test(trimmed)) return trimmed;
  return null;
}
