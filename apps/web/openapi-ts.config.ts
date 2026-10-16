import { defineConfig } from '@hey-api/openapi-ts';

export default defineConfig({
  input: '../../docs/api/openapi.yaml',
  output: {
    entryFile: false,
    path: './src/lib/api/generated',
    tsConfigPath: null,
  },
  plugins: ['@hey-api/typescript'],
});
