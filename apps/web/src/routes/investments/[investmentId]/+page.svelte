<script lang="ts">
  import type { PageProps } from './$types';
  import MoneyValue from '#lib/components/MoneyValue.svelte';
  import ScreenHeading from '#lib/components/ScreenHeading.svelte';
  import StatusBadge from '#lib/components/StatusBadge.svelte';
  import { formatDate, formatMoney, formatTaxRate } from '#lib/format.ts';

  let { data }: PageProps = $props();
  let investment = $derived(data.investment);
</script>

<svelte:head>
  <title>Detalhe do investimento | Carteira</title>
</svelte:head>

<ScreenHeading
  eyebrow="DETALHE DO INVESTIMENTO"
  title={investment.ownerName}
  backHref="/investments"
  backLabel="Voltar para a lista"
>
  <p class="page-lede">
    Criado em {formatDate(investment.createdOn)} <span class="inline-divider">·</span>
    <StatusBadge status={investment.status} />
  </p>
  {#snippet actions()}
    {#if investment.status === 'ACTIVE'}
      <a
        class="button button-primary"
        href={'/investments/' + encodeURIComponent(investment.id) + '/withdraw'}
      >
        Resgatar investimento
      </a>
    {/if}
  {/snippet}
</ScreenHeading>

<section class="balance-hero" aria-label="Saldo bruto">
  <div>
    <p class="eyebrow">
      {investment.status === 'ACTIVE' ? 'SALDO BRUTO ESPERADO' : 'SALDO BRUTO NA DATA DO RESGATE'}
    </p>
    <p class="hero-amount"><MoneyValue value={investment.grossBalance} emphasis /></p>
    <p class="hero-caption">
      {investment.status === 'ACTIVE'
        ? 'Posição calculada até ' + formatDate(investment.asOfDate)
        : 'Posição registrada em ' + formatDate(investment.asOfDate)}
    </p>
  </div>
  <div class="hero-mark" aria-hidden="true">↗</div>
</section>

<section class="metric-grid" aria-label="Resumo financeiro">
  <article class="metric-card">
    <p class="metric-label">Valor inicial</p>
    <p class="metric-value"><MoneyValue value={investment.initialAmount} /></p>
    <p class="metric-note">Principal aplicado</p>
  </article>
  <article class="metric-card metric-card-accent">
    <p class="metric-label">Ganhos acumulados</p>
    <p class="metric-value"><MoneyValue value={investment.gain} /></p>
    <p class="metric-note">{investment.completedPeriods} períodos mensais completos</p>
  </article>
  <article class="metric-card">
    <p class="metric-label">Data de referência</p>
    <p class="metric-value">{formatDate(investment.asOfDate)}</p>
    <p class="metric-note">
      {investment.status === 'ACTIVE' ? 'Posição atual' : 'Data efetiva do resgate'}
    </p>
  </article>
</section>

{#if investment.withdrawal}
  <section class="content-card receipt-card" aria-labelledby="withdrawal-history-title">
    <div class="section-heading">
      <div>
        <p class="eyebrow">HISTÓRICO</p>
        <h2 id="withdrawal-history-title">Resgate realizado</h2>
      </div>
      <span class="receipt-date">{formatDate(investment.withdrawal.withdrawnOn)}</span>
    </div>

    <dl class="receipt-grid">
      <div>
        <dt>Valor bruto</dt>
        <dd>{formatMoney(investment.withdrawal.grossAmount)}</dd>
      </div>
      <div>
        <dt>Ganhos tributáveis</dt>
        <dd>{formatMoney(investment.withdrawal.gainAmount)}</dd>
      </div>
      <div>
        <dt>Alíquota aplicada</dt>
        <dd>{formatTaxRate(investment.withdrawal.taxRate)}</dd>
      </div>
      <div>
        <dt>Imposto retido</dt>
        <dd>{formatMoney(investment.withdrawal.taxAmount)}</dd>
      </div>
      <div class="receipt-net">
        <dt>Valor líquido recebido</dt>
        <dd>{formatMoney(investment.withdrawal.netAmount)}</dd>
      </div>
      <div>
        <dt>Versão do cálculo</dt>
        <dd>{investment.withdrawal.calculationVersion}</dd>
      </div>
    </dl>
  </section>
{:else}
  <section class="content-card detail-note">
    <span class="note-icon" aria-hidden="true">i</span>
    <div>
      <h2>Como ler seu saldo</h2>
      <p>
        O saldo e os ganhos são retornados pela API na data de referência. O valor mostrado é bruto e não
        inclui imposto de resgate.
      </p>
    </div>
  </section>
{/if}
