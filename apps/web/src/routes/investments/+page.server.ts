import { error } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import { ApiError } from '#lib/api/request.ts';
import { listInvestments } from '#lib/api/server.ts';
import { compareIsoLocalDates, isSupportedIsoLocalDate } from '#lib/civil-date.ts';

const DEFAULT_LIMIT = 20;
const MAX_TRAIL_LENGTH = 20;
const MAX_CURSOR_LENGTH = 128;

function isValidCursorToken(value: string): boolean {
  if (value.length < 1 || value.length > MAX_CURSOR_LENGTH
      || !/^[A-Za-z0-9_-]+$/.test(value) || value.length % 4 === 1) return false;

  try {
    const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
    const binary = atob(base64 + '='.repeat((4 - (base64.length % 4)) % 4));
    const canonical = btoa(binary).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
    if (canonical !== value) return false;

    const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    const decoded = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
    const separator = decoded.indexOf(':');
    if (separator <= 0 || separator !== decoded.lastIndexOf(':')) return false;

    const date = decoded.slice(0, separator);
    const id = decoded.slice(separator + 1);
    return isSupportedIsoLocalDate(date) && /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(id);
  } catch {
    return false;
  }
}

function hasValidTrail(trail: string[]): boolean {
  return trail.every((token, index) => token === '' ? index === 0 : isValidCursorToken(token));
}

export const load: PageServerLoad = async ({ url }) => {
  const ownerName = (url.searchParams.get('ownerName') ?? '').trim();
  const createdFrom = url.searchParams.get('createdFrom') ?? '';
  const createdTo = url.searchParams.get('createdTo') ?? '';
  const rawLimit = url.searchParams.get('limit');
  const parsedLimit = rawLimit === null ? DEFAULT_LIMIT : /^\d+$/.test(rawLimit) ? Number(rawLimit) : NaN;
  if (!Number.isSafeInteger(parsedLimit) || parsedLimit < 1 || parsedLimit > 100) {
    error(400, 'O limite da página deve ser um número inteiro entre 1 e 100.');
  }
  const limit = parsedLimit;
  let filterError = '';
  if (createdFrom && !isSupportedIsoLocalDate(createdFrom)) {
    filterError = 'Informe uma data inicial válida no formato ISO (AAAA-MM-DD).';
  } else if (createdTo && !isSupportedIsoLocalDate(createdTo)) {
    filterError = 'Informe uma data final válida no formato ISO (AAAA-MM-DD).';
  } else if (createdFrom && createdTo && (compareIsoLocalDates(createdFrom, createdTo) ?? 1) > 0) {
    filterError = 'A data inicial não pode ser posterior à data final.';
  }

  if (filterError) {
    return {
      page: { items: [], nextCursor: null, limit },
      ownerName,
      createdFrom,
      createdTo,
      limit,
      cursor: null,
      trail: [],
      filterError
    };
  }

  let cursor = url.searchParams.get('cursor');
  const trail = url.searchParams.getAll('trail').slice(-MAX_TRAIL_LENGTH);
  if (!hasValidTrail(trail)) {
    trail.length = 0;
    cursor = null;
  }
  if (cursor !== null && !isValidCursorToken(cursor)) {
    trail.length = 0;
    cursor = null;
  }
  if (cursor === null) {
    trail.length = 0;
  }
  const query = new URLSearchParams({ limit: String(limit) });

  if (ownerName.length > 0) query.set('ownerName', ownerName);
  if (createdFrom) query.set('createdFrom', createdFrom);
  if (createdTo) query.set('createdTo', createdTo);
  if (cursor) query.set('cursor', cursor);

  try {
    const result = await listInvestments(query);
    return {
      page: result,
      ownerName,
      createdFrom,
      createdTo,
      limit,
      cursor,
      trail,
      filterError: ''
    };
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 400) {
      error(400, 'O filtro ou cursor de paginação não foi aceito.');
    }
    error(503, 'Não foi possível consultar os investimentos agora.');
  }
};
