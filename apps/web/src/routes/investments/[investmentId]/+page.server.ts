import { error } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';
import { ApiError } from '#lib/api/request.ts';
import { getInvestment } from '#lib/api/server.ts';

export const csr = false;

export const load: PageServerLoad = async ({ params }) => {
  try {
    return {
      investment: await getInvestment(params.investmentId)
    };
  } catch (cause) {
    if (cause instanceof ApiError && cause.status === 404) {
      error(404, 'Investimento não encontrado.');
    }
    error(503, 'Não foi possível carregar os dados do investimento.');
  }
};
