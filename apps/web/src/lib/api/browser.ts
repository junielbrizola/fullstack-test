import { API_PUBLIC_BASE_URL } from '$app/env/public';
import { encodeJson, requestJson } from './request';
import type {
  CreateInvestmentRequest,
  InvestmentView,
  WithdrawalDateRequest,
  WithdrawalResult
} from './types';

export function createInvestment(
  payload: CreateInvestmentRequest,
  idempotencyKey: string
): Promise<InvestmentView> {
  return requestJson<InvestmentView>(
    fetch,
    API_PUBLIC_BASE_URL,
    '/investments',
    {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: encodeJson(payload)
    }
  );
}

export function getInvestment(investmentId: string): Promise<InvestmentView> {
  return requestJson<InvestmentView>(
    fetch,
    API_PUBLIC_BASE_URL,
    '/investments/' + encodeURIComponent(investmentId)
  );
}

export function previewWithdrawal(
  investmentId: string,
  payload: WithdrawalDateRequest
): Promise<WithdrawalResult> {
  return requestJson<WithdrawalResult>(
    fetch,
    API_PUBLIC_BASE_URL,
    '/investments/' + encodeURIComponent(investmentId) + '/withdrawal-previews',
    {
      method: 'POST',
      body: encodeJson(payload)
    }
  );
}

export function confirmWithdrawal(
  investmentId: string,
  payload: WithdrawalDateRequest,
  idempotencyKey: string
): Promise<WithdrawalResult> {
  return requestJson<WithdrawalResult>(
    fetch,
    API_PUBLIC_BASE_URL,
    '/investments/' + encodeURIComponent(investmentId) + '/withdrawals',
    {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey
      },
      body: encodeJson(payload)
    }
  );
}
