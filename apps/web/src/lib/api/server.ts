import { API_INTERNAL_BASE_URL } from '$app/env/private';
import { requestJson } from './request';
import type { InvestmentPage, InvestmentView } from './types';

export function listInvestments(query: URLSearchParams): Promise<InvestmentPage> {
  return requestJson<InvestmentPage>(
    globalThis.fetch,
    API_INTERNAL_BASE_URL,
    '/investments?' + query.toString()
  );
}

export function getInvestment(investmentId: string): Promise<InvestmentView> {
  return requestJson<InvestmentView>(
    globalThis.fetch,
    API_INTERNAL_BASE_URL,
    '/investments/' + encodeURIComponent(investmentId)
  );
}
