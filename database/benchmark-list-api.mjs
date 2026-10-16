#!/usr/bin/env node

import { createHash } from 'node:crypto';
import { performance } from 'node:perf_hooks';

const DEFAULTS = {
  baseUrl: 'http://localhost:8080',
  limit: 100,
  concurrency: [1, 8, 16, 32],
  warmupSeconds: 5,
  durationSeconds: 10,
  timeoutSeconds: 10,
  json: false,
};
const MAX_LATENCY_SAMPLES = 100_000;

const USAGE = `
Benchmark GET-only da listagem local de investimentos.

Uso:
  node database/benchmark-list-api.mjs --owner-name <titular-sintetico> [opções]

Opções:
  --base-url <url>           API local (padrão: ${DEFAULTS.baseUrl})
  --owner-name <nome>        Titular com dados sintéticos a consultar (obrigatório)
  --limit <1..100>           Tamanho da página (padrão: ${DEFAULTS.limit})
  --concurrency <lista>      Níveis separados por vírgula (padrão: 1,8,16,32)
  --warmup-seconds <n>       Aquecimento por nível, 0..600 (padrão: ${DEFAULTS.warmupSeconds})
  --duration-seconds <n>     Duração da carga por nível, 1..600 (padrão: ${DEFAULTS.durationSeconds})
  --timeout-seconds <n>      Timeout de cada requisição, 1..120 (padrão: ${DEFAULTS.timeoutSeconds})
  --json                     Imprime resultados em JSON
  --help                     Mostra esta ajuda
`;

function fail(message) {
  console.error(`Erro: ${message}\n${USAGE}`);
  process.exitCode = 2;
}

function numericOption(name, value, min, max) {
  if (!/^\d+$/.test(value)) throw new Error(`${name} deve ser um inteiro entre ${min} e ${max}.`);
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < min || parsed > max) {
    throw new Error(`${name} deve ser um inteiro entre ${min} e ${max}.`);
  }
  return parsed;
}

function parseArguments(argv) {
  const options = { ...DEFAULTS };
  let ownerName;

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === '--help') return { help: true };
    if (argument === '--json') {
      options.json = true;
      continue;
    }

    const value = argv[index + 1];
    if (value === undefined || value.startsWith('--')) {
      throw new Error(`Falta o valor de ${argument}.`);
    }
    index += 1;

    switch (argument) {
      case '--base-url':
        options.baseUrl = value;
        break;
      case '--owner-name':
        ownerName = value;
        break;
      case '--limit':
        options.limit = numericOption(argument, value, 1, 100);
        break;
      case '--warmup-seconds':
        options.warmupSeconds = numericOption(argument, value, 0, 600);
        break;
      case '--duration-seconds':
        options.durationSeconds = numericOption(argument, value, 1, 600);
        break;
      case '--timeout-seconds':
        options.timeoutSeconds = numericOption(argument, value, 1, 120);
        break;
      case '--concurrency': {
        const levels = value.split(',').map((part) => numericOption(argument, part, 1, 256));
        if (levels.length > 8 || new Set(levels).size !== levels.length) {
          throw new Error('--concurrency aceita de 1 a 8 níveis distintos.');
        }
        options.concurrency = levels;
        break;
      }
      default:
        throw new Error(`Opção desconhecida: ${argument}.`);
    }
  }

  if (ownerName === undefined || ownerName.trim().length === 0 || Array.from(ownerName.trim()).length > 100) {
    throw new Error('--owner-name é obrigatório e deve ter entre 1 e 100 caracteres.');
  }

  const baseUrl = new URL(options.baseUrl);
  if (!['http:', 'https:'].includes(baseUrl.protocol)
      || !['localhost', '127.0.0.1', '[::1]'].includes(baseUrl.hostname)
      || baseUrl.username !== '' || baseUrl.password !== '') {
    throw new Error('--base-url deve apontar para localhost e não pode conter credenciais.');
  }
  options.endpoint = new URL('/api/v1/investments', baseUrl);
  options.endpoint.searchParams.set('ownerName', ownerName.trim());
  options.endpoint.searchParams.set('limit', String(options.limit));
  options.ownerFilterSha256 = createHash('sha256').update(ownerName.trim()).digest('hex');
  return options;
}

function percentile(sortedValues, fraction) {
  if (sortedValues.length === 0) return null;
  const index = Math.max(0, Math.ceil(sortedValues.length * fraction) - 1);
  return sortedValues[index];
}

function classifyError(error) {
  if (error?.name === 'TimeoutError' || error?.name === 'AbortError') return 'TIMEOUT';
  return 'NETWORK_ERROR';
}

async function request(endpoint, timeoutSeconds) {
  const startedAt = performance.now();
  try {
    const response = await fetch(endpoint, {
      method: 'GET',
      headers: { accept: 'application/json' },
      redirect: 'error',
      signal: AbortSignal.timeout(timeoutSeconds * 1000),
    });
    await response.arrayBuffer();
    return {
      latencyMs: performance.now() - startedAt,
      failure: response.ok ? null : `HTTP_${response.status}`,
    };
  } catch (error) {
    return {
      latencyMs: performance.now() - startedAt,
      failure: classifyError(error),
    };
  }
}

async function verifyDataset(endpoint, timeoutSeconds) {
  let response;
  try {
    response = await fetch(endpoint, {
      method: 'GET',
      headers: { accept: 'application/json' },
      redirect: 'error',
      signal: AbortSignal.timeout(timeoutSeconds * 1000),
    });
  } catch (error) {
    throw new Error(`A pré-verificação da API falhou (${classifyError(error)}).`);
  }
  if (!response.ok) throw new Error(`A pré-verificação da API respondeu HTTP ${response.status}.`);

  let page;
  try {
    page = await response.json();
  } catch {
    throw new Error('A pré-verificação recebeu uma resposta que não é JSON válido.');
  }
  if (!Array.isArray(page.items) || page.items.length === 0) {
    throw new Error('A pré-verificação não encontrou investimentos; confirme o titular sintético.');
  }
  return page.items.length;
}

async function runStage({ endpoint, concurrency, seconds, timeoutSeconds, collect }) {
  const samples = [];
  let measuredSamples = 0;
  let maximumLatencyMs = 0;
  const failures = {};
  let successes = 0;
  let attempts = 0;
  const startedAt = performance.now();
  const deadline = startedAt + seconds * 1000;

  await Promise.all(Array.from({ length: concurrency }, async () => {
    while (performance.now() < deadline) {
      const result = await request(endpoint, Math.min(timeoutSeconds, seconds));
      attempts += 1;
      maximumLatencyMs = Math.max(maximumLatencyMs, result.latencyMs);
      if (collect) {
        measuredSamples += 1;
        if (samples.length < MAX_LATENCY_SAMPLES) {
          samples.push(result.latencyMs);
        } else {
          const replacementIndex = Math.floor(Math.random() * measuredSamples);
          if (replacementIndex < MAX_LATENCY_SAMPLES) samples[replacementIndex] = result.latencyMs;
        }
      }
      if (result.failure === null) {
        successes += 1;
      } else {
        failures[result.failure] = (failures[result.failure] ?? 0) + 1;
      }
    }
  }));

  const elapsedSeconds = (performance.now() - startedAt) / 1000;
  samples.sort((left, right) => left - right);
  return {
    attempts,
    successes,
    failures,
    latencyObservationCount: measuredSamples,
    latencySampleCount: samples.length,
    elapsedSeconds,
    loadWindowSeconds: seconds,
    drainSeconds: Math.max(0, elapsedSeconds - seconds),
    requestsPerSecond: successes / elapsedSeconds,
    latencyMs: collect ? {
      p50: percentile(samples, 0.50),
      p95: percentile(samples, 0.95),
      p99: percentile(samples, 0.99),
      max: measuredSamples === 0 ? null : maximumLatencyMs,
    } : undefined,
  };
}

function round(value) {
  return value === null || value === undefined ? null : Math.round(value * 100) / 100;
}

function present(results, endpoint, datasetPageSize, json) {
  const report = {
    endpoint: `${endpoint.origin}${endpoint.pathname}`,
    method: 'GET',
    datasetPageSize,
    parameters: {
      limit: results[0]?.limit,
      concurrency: results.map(({ concurrency }) => concurrency),
      warmupSeconds: results[0]?.warmupSeconds,
      durationSeconds: results[0]?.loadWindowSeconds,
      timeoutSeconds: results[0]?.timeoutSeconds,
      ownerFilterSha256: results[0]?.ownerFilterSha256,
    },
    levels: results.map(({ concurrency, warmup, ...load }) => ({
      concurrency,
      warmupRequests: warmup.attempts,
      warmupErrors: warmup.failures,
      requests: load.attempts,
      successfulRequests: load.successes,
      errors: load.failures,
      elapsedSeconds: round(load.elapsedSeconds),
      loadWindowSeconds: load.loadWindowSeconds,
      drainSeconds: round(load.drainSeconds),
      successfulRequestsPerSecond: round(load.requestsPerSecond),
      latencySampleCount: load.latencySampleCount,
      latencyObservationCount: load.latencyObservationCount,
      latencyMs: Object.fromEntries(Object.entries(load.latencyMs).map(([key, value]) => [key, round(value)])),
    })),
  };

  if (json) {
    console.log(JSON.stringify(report, null, 2));
    return;
  }

  console.log(`Benchmark GET ${report.endpoint}`);
  console.log(`Amostra do conjunto (pré-verificação): ${datasetPageSize} registros`);
  console.log(`Filtro do titular (SHA-256): ${report.parameters.ownerFilterSha256}`);
  console.log('Conc. | Req. | Erros aq. | Erros | Req/s ok | Amostras | Drenagem s | p50 ms | p95 ms | p99 ms | Máx. ms');
  for (const level of report.levels) {
    const errors = Object.values(level.errors).reduce((sum, count) => sum + count, 0);
    const warmupErrors = Object.values(level.warmupErrors).reduce((sum, count) => sum + count, 0);
    const latency = level.latencyMs;
    console.log([
      String(level.concurrency).padStart(5),
      String(level.requests).padStart(5),
      String(warmupErrors).padStart(9),
      String(errors).padStart(5),
      String(level.successfulRequestsPerSecond).padStart(8),
      String(level.latencySampleCount).padStart(8),
      String(level.drainSeconds).padStart(11),
      String(latency.p50).padStart(7),
      String(latency.p95).padStart(7),
      String(latency.p99).padStart(7),
      String(latency.max).padStart(7),
    ].join(' | '));
  }
}

async function main() {
  let options;
  try {
    options = parseArguments(process.argv.slice(2));
  } catch (error) {
    fail(error.message);
    return;
  }
  if (options.help) {
    console.log(USAGE.trim());
    return;
  }

  let datasetPageSize;
  try {
    datasetPageSize = await verifyDataset(options.endpoint, options.timeoutSeconds);
  } catch (error) {
    fail(error.message);
    return;
  }

  const results = [];
  for (const concurrency of options.concurrency) {
    const warmup = options.warmupSeconds === 0
      ? { attempts: 0, failures: {} }
      : await runStage({
        endpoint: options.endpoint,
        concurrency,
        seconds: options.warmupSeconds,
        timeoutSeconds: options.timeoutSeconds,
        collect: false,
      });
    const load = await runStage({
      endpoint: options.endpoint,
      concurrency,
      seconds: options.durationSeconds,
      timeoutSeconds: options.timeoutSeconds,
      collect: true,
    });
    results.push({
      concurrency,
      warmup,
      ...load,
      limit: options.limit,
      warmupSeconds: options.warmupSeconds,
      timeoutSeconds: options.timeoutSeconds,
      ownerFilterSha256: options.ownerFilterSha256,
    });
  }

  present(results, options.endpoint, datasetPageSize, options.json);
  const hasFailures = results.some(({ warmup, failures }) =>
    Object.keys(warmup.failures).length > 0 || Object.keys(failures).length > 0);
  if (hasFailures) process.exitCode = 1;
}

await main();
