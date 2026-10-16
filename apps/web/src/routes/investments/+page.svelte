<script lang="ts">
  import type { PageProps } from './$types';
  import MoneyValue from '#lib/components/MoneyValue.svelte';
  import ScreenHeading from '#lib/components/ScreenHeading.svelte';
  import StatusBadge from '#lib/components/StatusBadge.svelte';
  import { isNativeDateInputValue } from '#lib/civil-date.ts';
  import { formatDate, formatMoneyCents, formatTaxRate, sumMoneyCents } from '#lib/format.ts';

  let { data }: PageProps = $props();
  let ownerName = $state(data.ownerName);
  let createdFrom = $state(data.createdFrom);
  let createdTo = $state(data.createdTo);

  $effect(() => {
    ownerName = data.ownerName;
    createdFrom = data.createdFrom;
    createdTo = data.createdTo;
  });

  function pageHref(direction: 'next' | 'previous'): string | null {
    const params = new URLSearchParams();
    if (data.ownerName) params.set('ownerName', data.ownerName);
    if (data.createdFrom) params.set('createdFrom', data.createdFrom);
    if (data.createdTo) params.set('createdTo', data.createdTo);
    params.set('limit', String(data.limit));

    if (direction === 'next' && data.page.nextCursor) {
      const nextTrail = [...data.trail, data.cursor ?? ''];
      for (const token of nextTrail) params.append('trail', token);
      params.set('cursor', data.page.nextCursor);
      return '/investments?' + params.toString();
    }

    if (direction === 'previous' && data.trail.length > 0) {
      const previousCursor = data.trail[data.trail.length - 1];
      for (const token of data.trail.slice(0, -1)) params.append('trail', token);
      if (previousCursor) params.set('cursor', previousCursor);
      return '/investments?' + params.toString();
    }

    return null;
  }

  const nextHref = $derived(pageHref('next'));
  const previousHref = $derived(pageHref('previous'));
  const hasActiveFilters = $derived(Boolean(ownerName || createdFrom || createdTo));
  const visibleBalanceCents = $derived(sumMoneyCents(data.page.items.map((item) => item.grossBalance)));

  function submitChangedFilter(event: Event): void {
    const target = event.target;
    if (!(target instanceof HTMLInputElement) || !target.form) return;
    if (target.name === 'ownerName' || target.name === 'createdFrom' || target.name === 'createdTo') {
      target.form.requestSubmit();
    }
  }
</script>

<svelte:head>
  <title>Investimentos | Carteira</title>
  <meta name="description" content="Consulte, crie e acompanhe investimentos e resgates." />
</svelte:head>

<section class="portfolio-balance" aria-label="Saldo bruto da página visível">
  <div>
    <p class="balance-label">Balance</p>
    <p class="balance-caption">Soma dos saldos brutos desta página</p>
  </div>
  <strong class="balance-amount">{formatMoneyCents(visibleBalanceCents)}</strong>
  <span class="balance-page-count">{data.page.items.length} investimentos nesta página</span>
</section>

<section class="content-card" aria-labelledby="investment-list-title">
  <ScreenHeading title="Investimentos" titleId="investment-list-title" variant="card">
    {#snippet actions()}
      <form class="list-filter-form" method="GET" action="/investments" onchange={submitChangedFilter}>
        <div class="filter-fields">
          <div class="filter-field" class:has-value={isNativeDateInputValue(createdFrom)}>
            <label for="created-from-filter">Data inicial</label>
            <span class="filter-field-icon" aria-hidden="true">
              <svg viewBox="0 0 20 20" focusable="false"><rect x="2.5" y="4" width="15" height="13" rx="2" /><path d="M6 2.5v3M14 2.5v3M2.5 8h15" /></svg>
            </span>
            <input id="created-from-filter" name="createdFrom" type="date" bind:value={createdFrom} />
          </div>
          <div class="filter-field" class:has-value={isNativeDateInputValue(createdTo)}>
            <label for="created-to-filter">Data final</label>
            <span class="filter-field-icon" aria-hidden="true">
              <svg viewBox="0 0 20 20" focusable="false"><rect x="2.5" y="4" width="15" height="13" rx="2" /><path d="M6 2.5v3M14 2.5v3M2.5 8h15" /></svg>
            </span>
            <input id="created-to-filter" name="createdTo" type="date" bind:value={createdTo} />
          </div>
          <div class="filter-field filter-owner-field" class:has-value={ownerName.length > 0}>
            <label for="owner-filter">Titular</label>
            <span class="filter-field-icon" aria-hidden="true">
              <svg viewBox="0 0 20 20" focusable="false"><circle cx="8.5" cy="8.5" r="5.5" /><path d="m12.5 12.5 4 4" /></svg>
            </span>
            <input
              id="owner-filter"
              name="ownerName"
              type="search"
              maxlength="100"
              bind:value={ownerName}
              autocomplete="off"
            />
          </div>
          <input type="hidden" name="limit" value={data.limit} />
          <div class="filter-actions">
            {#if hasActiveFilters}
              <a class="button button-secondary button-small" href="/investments">Limpar</a>
            {/if}
            <a class="button button-primary button-small" href="/investments/new">
              <span aria-hidden="true">＋</span> Novo
            </a>
          </div>
        </div>
        <p class="list-filter-note visually-hidden">
          Titular por correspondência exata e sensível a maiúsculas; datas de criação inclusivas.
        </p>
      </form>
    {/snippet}
  </ScreenHeading>

  {#if data.filterError}
    <div class="filter-error" role="alert">
      <p>{data.filterError}</p>
      <a class="text-link" href="/investments">Limpar filtros e tentar novamente</a>
    </div>
  {:else}
    <div class="table-scroll">
      <table class="investment-table">
        <thead>
          <tr>
            <th scope="col">Data</th>
            <th scope="col">Valor</th>
            <th scope="col">Saldo atual</th>
            <th scope="col">Status</th>
            <th scope="col"><span class="visually-hidden">Ações</span></th>
          </tr>
        </thead>
        <tbody>
          {#if data.page.items.length === 0}
            <tr>
              <td class="empty-table-cell" colspan="5">
                <div class="empty-state">
                  <div class="state-symbol" aria-hidden="true">↗</div>
                  <h3>{hasActiveFilters ? 'Nenhum investimento encontrado' : 'Sua carteira começa aqui'}</h3>
                  <p>
                    {hasActiveFilters
                      ? 'Confira os critérios ou remova os filtros para consultar novamente.'
                      : 'Crie o primeiro investimento para acompanhar seu saldo e seus ganhos.'}
                  </p>
                  {#if hasActiveFilters}
                    <a class="button button-secondary" href="/investments">Limpar filtros</a>
                  {:else}
                    <a class="button button-primary" href="/investments/new">Criar investimento</a>
                  {/if}
                </div>
              </td>
            </tr>
          {:else}
            {#each data.page.items as investment (investment.id)}
              <tr>
                <td data-label="Data">
                  <span class="created-date">{formatDate(investment.createdOn)}</span>
                  <small class="owner-cell">{investment.ownerName}</small>
                </td>
                <td data-label="Valor"><MoneyValue value={investment.initialAmount} /></td>
                <td data-label="Saldo atual">
                  <span class="balance-cell">
                    <MoneyValue value={investment.grossBalance} emphasis />
                    <small>
                      em {formatDate(investment.asOfDate)} · ganho <MoneyValue value={investment.gain} />
                      · {investment.completedPeriods} períodos
                    </small>
                  </span>
                </td>
                <td data-label="Status"><StatusBadge status={investment.status} /></td>
                <td class="table-action" data-label="Ações">
                  <div class="investment-actions">
                    <a
                      class="button button-primary button-small"
                      href={'/investments/' + encodeURIComponent(investment.id)}
                      aria-label={'Ver detalhes do investimento de ' + investment.ownerName}
                    >Ver</a>
                    {#if investment.status === 'ACTIVE'}
                      <a
                        class="button button-danger button-small"
                        href={'/investments/' + encodeURIComponent(investment.id) + '/withdraw'}
                        aria-label={'Resgatar investimento de ' + investment.ownerName}
                      >Resgatar</a>
                    {/if}
                  </div>
                  {#if investment.withdrawal}
                    <span class="visually-hidden">
                      Resgatado em {formatDate(investment.withdrawal.withdrawnOn)}. Valor líquido:
                      {formatMoneyCents(sumMoneyCents([investment.withdrawal.netAmount]))}.
                      Imposto: {formatTaxRate(investment.withdrawal.taxRate)}
                      ({formatMoneyCents(sumMoneyCents([investment.withdrawal.taxAmount]))}).
                    </span>
                  {/if}
                </td>
              </tr>
            {/each}
          {/if}
        </tbody>
      </table>
    </div>
  {/if}

  <nav class="pagination" aria-label="Paginação de investimentos">
    <span class="pagination-context">Exibindo até {data.limit} por página</span>
    <div class="pagination-actions">
      {#if previousHref}
        <a class="button button-secondary button-small" href={previousHref}>Anterior</a>
      {:else}
        <span class="button button-secondary button-small button-disabled" aria-disabled="true">
          Anterior
        </span>
      {/if}
      {#if nextHref}
        <a class="button button-secondary button-small" href={nextHref}>
          Próxima página <span aria-hidden="true">→</span>
        </a>
      {:else}
        <span class="button button-secondary button-small button-disabled" aria-disabled="true">
          Próxima página
        </span>
      {/if}
    </div>
  </nav>
</section>
