import { defineEnvVars } from '@sveltejs/kit/env';

export const variables = defineEnvVars({
  API_INTERNAL_BASE_URL: {},
  API_PUBLIC_BASE_URL: {
    public: true
  },
  PUBLIC_BUSINESS_TIME_ZONE: {
    public: true
  }
});
