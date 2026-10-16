import type { Problem } from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem;

  constructor(problem: Problem) {
    super(problem.detail);
    this.name = 'ApiError';
    this.status = problem.status;
    this.problem = problem;
  }
}

export class ApiTimeoutError extends Error {
  constructor() {
    super('A API não respondeu dentro do prazo de 15 segundos.');
    this.name = 'ApiTimeoutError';
  }
}

function joinApiUrl(baseUrl: string, path: string): string {
  return baseUrl.replace(/\/+$/, '') + '/api/v1' + path;
}

function isProblem(value: unknown): value is Problem {
  if (typeof value !== 'object' || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.type === 'string' &&
    typeof candidate.title === 'string' &&
    typeof candidate.status === 'number' &&
    typeof candidate.detail === 'string'
  );
}

export async function requestJson<T>(
  fetcher: typeof fetch,
  baseUrl: string,
  path: string,
  init: RequestInit = {}
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json, application/problem+json');
  if (init.body !== undefined) headers.set('Content-Type', 'application/json');

  const timeoutSignal = AbortSignal.timeout(15_000);
  const signal = init.signal ? AbortSignal.any([init.signal, timeoutSignal]) : timeoutSignal;

  try {
    const response = await fetcher(joinApiUrl(baseUrl, path), {
      ...init,
      headers,
      signal
    });

    if (!response.ok) {
      let problem: Problem = {
        type: 'about:blank',
        title: 'Falha na solicitação',
        status: response.status,
        detail: 'A API não conseguiu concluir a solicitação.'
      };

      try {
        const body: unknown = await response.json();
        if (isProblem(body)) problem = body;
      } catch {
        if (timeoutSignal.aborted) throw new ApiTimeoutError();
        // Respostas sem Problem Details mantêm uma mensagem segura e genérica.
      }

      throw new ApiError(problem);
    }

    return (await response.json()) as T;
  } catch (cause) {
    if (timeoutSignal.aborted) throw new ApiTimeoutError();
    throw cause;
  }
}

export function encodeJson(value: unknown): string {
  return JSON.stringify(value);
}
