import type {
  CreateInvestmentRequest as ApiCreateInvestmentRequest,
  InvestmentPage as ApiInvestmentPage,
  InvestmentView as ApiInvestmentView,
  Money as ApiMoney,
  Principal as ApiPrincipal,
  Problem as ApiProblem,
  WithdrawalDateRequest as ApiWithdrawalDateRequest,
  WithdrawalResult as ApiWithdrawalResult,
} from './generated/types.gen';

export type Money = ApiMoney;
export type Principal = ApiPrincipal;
export type CreateInvestmentRequest = ApiCreateInvestmentRequest;
export type WithdrawalDateRequest = ApiWithdrawalDateRequest;
export type InvestmentPage = ApiInvestmentPage;
export type InvestmentView = ApiInvestmentView;
export type WithdrawalResult = ApiWithdrawalResult;
export type Problem = ApiProblem;
