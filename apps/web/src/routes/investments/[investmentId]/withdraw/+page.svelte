<script lang="ts">
  import { onMount } from 'svelte';
  import type { PageProps } from './$types';
  import { ApiError } from '#lib/api/request.ts';
  import {
    confirmWithdrawal,
    getInvestment,
    previewWithdrawal
  } from '#lib/api/browser.ts';
  import MoneyValue from '#lib/components/MoneyValue.svelte';
  import ProblemMessage from '#lib/components/ProblemMessage.svelte';
  import ScreenHeading from '#lib/components/ScreenHeading.svelte';
  import StatusBadge from '#lib/components/StatusBadge.svelte';
  import type { WithdrawalResult } from '#lib/api/types.ts';
  import {
    compareIsoLocalDates,
    isNativeDateInputValue,
    isSupportedIsoLocalDate
  } from '#lib/civil-date.ts';
  import { businessToday, formatDate, formatMoney, formatTaxRate } from '#lib/format.ts';

  const PENDING_WITHDRAWAL_KEY_PREFIX = 'coderockr.investments.pending-withdrawal.v1:';
  const EXTENDED_ISO_DATE_PATTERN = /(?:[0-9]{4}|-(?:000[1-9]|00[1-9][0-9]|0[1-9][0-9]{2}|[1-9][0-9]{3})|\+[1-9][0-9]{4,6})-(?:0[1-9]|1[0-2])-(?:0[1-9]|1[0-9]|2[0-9]|3[01])/;

  interface PendingWithdrawal {
    idempotencyKey: string;
    withdrawnOn: string;
  }

  let { data }: PageProps = $props();
  let withdrawnOn = $state(businessToday());
  let today = $state(businessToday());
  let withdrawalDateTouched = $state(false);
  let expandedDateEntry = $state(false);
  let preview = $state<WithdrawalResult | null>(null);
  let receipt = $state<WithdrawalResult | null>(null);
  let previewing = $state(false);
  let confirming = $state(false);
  let checking = $state(false);
  let formError = $state('');
  let outcomeUncertain = $state(false);
  let conflict = $state(false);
  let statusMessage = $state('');
  let idempotencyKey = $state<string | null>(null);
  let attemptedDate = $state<string | null>(null);
  let pendingRecoveryBlocked = $state(false);
  let recoveryCanBeReleased = $state(false);

  const investment = $derived(data.investment);
  const validDate = $derived(isValidWithdrawalDate(withdrawnOn, investment.createdOn, today));
  const dateLocked = $derived(attemptedDate !== null);

  function isValidWithdrawalDate(value: string, createdOn: string, todayValue: string): boolean {
    return (
      isSupportedIsoLocalDate(value) &&
      (compareIsoLocalDates(value, createdOn) ?? -1) >= 0 &&
      (compareIsoLocalDates(value, todayValue) ?? 1) <= 0
    );
  }

  function refreshBusinessDate(): void {
    const previousToday = today;
    const currentToday = businessToday();
    if (
      !withdrawalDateTouched &&
      attemptedDate === null &&
      preview === null &&
      !previewing &&
      receipt === null &&
      withdrawnOn === previousToday
    ) {
      withdrawnOn = currentToday;
    }
    today = currentToday;
    if (
      attemptedDate !== null &&
      receipt === null &&
      !isValidWithdrawalDate(attemptedDate, investment.createdOn, today)
    ) {
      outcomeUncertain = true;
      statusMessage = '';
      formError =
        'A data da tentativa pendente não é válida para o dia de negócio atual. Consulte a situação; o reenvio continua bloqueado até uma reconciliação segura.';
    }
  }

  function pendingWithdrawalStorageKey(): string {
    return PENDING_WITHDRAWAL_KEY_PREFIX + investment.id;
  }

  function isPendingWithdrawal(value: unknown): value is PendingWithdrawal {
    if (typeof value !== 'object' || value === null) return false;
    const candidate = value as Record<string, unknown>;
    return (
      typeof candidate.idempotencyKey === 'string' &&
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
        candidate.idempotencyKey
      ) &&
      isSupportedIsoLocalDate(candidate.withdrawnOn)
    );
  }

  function persistPendingWithdrawal(pending: PendingWithdrawal): boolean {
    try {
      sessionStorage.setItem(pendingWithdrawalStorageKey(), JSON.stringify(pending));
      return true;
    } catch {
      return false;
    }
  }

  function clearPendingWithdrawal(): void {
    try {
      sessionStorage.removeItem(pendingWithdrawalStorageKey());
    } catch {
      // A stale key remains safe: the detail endpoint exposes the committed withdrawal receipt.
    }
  }

  function discardUnreadablePendingWithdrawal(): boolean {
    try {
      sessionStorage.removeItem(pendingWithdrawalStorageKey());
      return true;
    } catch {
      return false;
    }
  }

  function apiMessage(cause: unknown, fallback: string): string {
    return cause instanceof ApiError ? cause.problem.detail : fallback;
  }

  async function handlePreview(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    refreshBusinessDate();
    if (outcomeUncertain) return;
    formError = '';
    statusMessage = '';
    outcomeUncertain = false;

    if (!isValidWithdrawalDate(withdrawnOn, investment.createdOn, today)) {
      formError = 'Escolha uma data entre a criação do investimento e hoje.';
      return;
    }

    previewing = true;
    try {
      const previewDate = attemptedDate ?? withdrawnOn;
      preview = await previewWithdrawal(investment.id, { withdrawnOn: previewDate });
      conflict = false;
      statusMessage = 'Prévia calculada. Revise os valores antes de confirmar.';
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        conflict = true;
        preview = null;
        formError = cause.problem.detail;
      } else {
        formError = apiMessage(cause, 'Não foi possível obter a prévia. Tente novamente.');
      }
    } finally {
      previewing = false;
    }
  }

  async function handleConfirm(): Promise<void> {
    refreshBusinessDate();
    if (!preview || confirming || outcomeUncertain) return;
    const confirmationDate = attemptedDate ?? withdrawnOn;
    if (preview.withdrawnOn !== confirmationDate) {
      preview = null;
      formError = 'A data selecionada mudou depois da prévia. Calcule uma nova prévia antes de confirmar.';
      return;
    }
    if (!isValidWithdrawalDate(confirmationDate, investment.createdOn, today)) {
      formError = 'Escolha uma data entre a criação do investimento e hoje.';
      return;
    }

    formError = '';
    outcomeUncertain = false;
    if (idempotencyKey === null) {
      if (typeof crypto.randomUUID !== 'function') {
        formError = 'Este navegador não oferece uma chave segura para confirmar o resgate.';
        return;
      }
      const newIdempotencyKey = crypto.randomUUID();
      const newAttemptedDate = withdrawnOn;
      idempotencyKey = newIdempotencyKey;
      attemptedDate = newAttemptedDate;
      if (!persistPendingWithdrawal({ idempotencyKey: newIdempotencyKey, withdrawnOn: newAttemptedDate })) {
        idempotencyKey = null;
        attemptedDate = null;
        formError = 'O navegador não permitiu guardar a chave da operação. Nenhuma solicitação foi enviada.';
        return;
      }
    }

    const stableDate = attemptedDate;
    const stableKey = idempotencyKey;
    if (stableDate === null || stableKey === null) {
      formError = 'Não foi possível preparar uma chave segura para a operação.';
      return;
    }

    confirming = true;
    try {
      receipt = await confirmWithdrawal(
        investment.id,
        { withdrawnOn: stableDate },
        stableKey
      );
      clearPendingWithdrawal();
      preview = null;
      statusMessage = 'O resgate foi confirmado pela API.';
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        conflict = true;
        formError = cause.problem.detail;
      } else if (cause instanceof ApiError && cause.status < 500) {
        formError = cause.problem.detail;
        clearPendingWithdrawal();
        idempotencyKey = null;
        attemptedDate = null;
        preview = null;
      } else {
        outcomeUncertain = true;
        formError =
          'A resposta do resgate não chegou. Consulte a situação antes de tentar novamente.';
      }
    } finally {
      confirming = false;
    }
  }

  async function reconcileWithdrawal(): Promise<void> {
    checking = true;
    recoveryCanBeReleased = false;
    refreshBusinessDate();
    formError = '';
    statusMessage = '';

    try {
      const current = await getInvestment(investment.id);
      if (current.status === 'WITHDRAWN' && current.withdrawal) {
        receipt = current.withdrawal;
        clearPendingWithdrawal();
        preview = null;
        outcomeUncertain = false;
        conflict = false;
        statusMessage = 'O resgate foi encontrado no histórico deste investimento.';
      } else if (current.status === 'ACTIVE') {
        if (attemptedDate !== null) withdrawnOn = attemptedDate;
        preview = null;
        conflict = false;
        if (pendingRecoveryBlocked) {
          outcomeUncertain = true;
          recoveryCanBeReleased = true;
          formError =
            'A API confirmou que o investimento continua ativo. Revise a carteira antes de liberar uma nova tentativa.';
        } else if (
          !isValidWithdrawalDate(attemptedDate ?? withdrawnOn, investment.createdOn, today)
        ) {
          outcomeUncertain = true;
          formError =
            'O investimento continua ativo, mas a data salva não é válida para o dia de negócio atual. O reenvio permanece bloqueado até uma reconciliação segura.';
        } else {
          outcomeUncertain = false;
          statusMessage =
            'O investimento continua ativo. A tentativa pode ser repetida com a mesma data e chave de operação.';
        }
      } else {
        formError = 'A API não retornou o histórico do resgate. Revise o detalhe do investimento.';
      }
    } catch (cause) {
      formError = apiMessage(cause, 'Não foi possível consultar a situação agora.');
    } finally {
      checking = false;
    }
  }

  function releaseAfterActiveReconciliation(): void {
    if (!recoveryCanBeReleased || checking) return;
    if (!discardUnreadablePendingWithdrawal()) {
      formError = 'Não foi possível limpar o estado pendente do navegador. Libere o armazenamento da sessão antes de continuar.';
      return;
    }

    idempotencyKey = null;
    attemptedDate = null;
    pendingRecoveryBlocked = false;
    recoveryCanBeReleased = false;
    outcomeUncertain = false;
    preview = null;
    conflict = false;
    withdrawalDateTouched = false;
    today = businessToday();
    withdrawnOn = today;
    formError = '';
    statusMessage = 'A API confirmou que o investimento segue ativo. Escolha a data e calcule uma nova prévia.';
  }

  onMount(() => {
    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') refreshBusinessDate();
    };
    const timer = window.setInterval(refreshBusinessDate, 60_000);
    window.addEventListener('focus', refreshBusinessDate);
    document.addEventListener('visibilitychange', refreshWhenVisible);
    refreshBusinessDate();

    return () => {
      window.clearInterval(timer);
      window.removeEventListener('focus', refreshBusinessDate);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
    };
  });

  onMount(() => {
    try {
      const rawPending = sessionStorage.getItem(pendingWithdrawalStorageKey());
      if (rawPending === null) return;

      const candidate: unknown = JSON.parse(rawPending);
      if (!isPendingWithdrawal(candidate)) {
        pendingRecoveryBlocked = true;
        outcomeUncertain = true;
        void reconcileWithdrawal();
        return;
      }

      idempotencyKey = candidate.idempotencyKey;
      attemptedDate = candidate.withdrawnOn;
      withdrawnOn = candidate.withdrawnOn;
      expandedDateEntry = !isNativeDateInputValue(withdrawnOn);
      outcomeUncertain = true;
      statusMessage = 'Uma tentativa de resgate sem confirmação foi recuperada. Consulte a situação.';
      void reconcileWithdrawal();
    } catch {
      formError = 'Não foi possível recuperar a tentativa de resgate. Consulte a situação antes de continuar.';
      pendingRecoveryBlocked = true;
      outcomeUncertain = true;
    }
  });
</script>

<svelte:head>
  <title>Resgatar investimento | Carteira</title>
</svelte:head>

<ScreenHeading
  eyebrow="RESGATE INTEGRAL"
  title="Resgatar investimento"
  backHref={'/investments/' + encodeURIComponent(investment.id)}
  backLabel="Voltar ao investimento"
>
  <p class="page-lede">{investment.ownerName} · criado em {formatDate(investment.createdOn)}</p>
  {#snippet actions()}
    <StatusBadge status={receipt || conflict ? 'WITHDRAWN' : investment.status} />
  {/snippet}
</ScreenHeading>

{#if receipt}
  <section class="content-card result-card" aria-labelledby="withdrawal-result-title">
    <div class="result-check" aria-hidden="true">✓</div>
    <p class="eyebrow">OPERAÇÃO REGISTRADA</p>
    <h2 id="withdrawal-result-title">Resgate confirmado</h2>
    <p class="result-date">Data efetiva: {formatDate(receipt.withdrawnOn)}</p>
    {#if statusMessage}
      <p class="success-message" role="status">{statusMessage}</p>
    {/if}
    <div class="result-net">
      <span>Valor líquido recebido</span>
      <strong>{formatMoney(receipt.netAmount)}</strong>
    </div>
    <dl class="receipt-grid">
      <div><dt>Valor bruto</dt><dd>{formatMoney(receipt.grossAmount)}</dd></div>
      <div><dt>Ganhos tributáveis</dt><dd>{formatMoney(receipt.gainAmount)}</dd></div>
      <div><dt>Alíquota aplicada</dt><dd>{formatTaxRate(receipt.taxRate)}</dd></div>
      <div><dt>Imposto retido</dt><dd>{formatMoney(receipt.taxAmount)}</dd></div>
    </dl>
    <a class="button button-primary" href={'/investments/' + encodeURIComponent(investment.id)}>
      Ver investimento
    </a>
  </section>
{:else if conflict || investment.status === 'WITHDRAWN'}
  <section class="state-panel state-panel-centered" aria-labelledby="withdrawal-conflict-title">
    <div class="state-symbol state-symbol-warning" aria-hidden="true">!</div>
    <p class="eyebrow">INVESTIMENTO INDISPONÍVEL</p>
    <h2 id="withdrawal-conflict-title">Este investimento já foi resgatado</h2>
    <p class="state-description">
      Um resgate integral já está registrado. Consulte o histórico para ver o valor líquido recebido.
    </p>
    {#if formError}<ProblemMessage message={formError} />{/if}
    <a class="button button-primary" href={'/investments/' + encodeURIComponent(investment.id)}>
      Ver histórico do resgate
    </a>
  </section>
{:else}
  <div class="withdrawal-layout">
    <section class="content-card withdrawal-form-card" aria-labelledby="withdrawal-form-title">
      <div class="form-card-heading">
        <span class="step-number" aria-hidden="true">01</span>
        <div>
          <h2 id="withdrawal-form-title">Escolha a data de resgate</h2>
          <p>O resgate é integral e não pode ser desfeito.</p>
        </div>
      </div>

      {#if formError}<ProblemMessage message={formError} />{/if}
      {#if statusMessage}<p class="success-message" role="status">{statusMessage}</p>{/if}

      <form onsubmit={handlePreview} novalidate>
        <div class="withdrawal-identity-grid" aria-label="Informações do investimento">
          <div class="field-group">
            <label for="withdrawal-investment-id">ID</label>
            <input id="withdrawal-investment-id" type="text" value={investment.id} readonly />
          </div>
          <div class="field-group">
            <label for="withdrawal-owner">Titular</label>
            <input id="withdrawal-owner" type="text" value={investment.ownerName} readonly />
          </div>
          <div class="field-group">
            <label for="withdrawal-amount">Valor inicial</label>
            <input id="withdrawal-amount" type="text" value={formatMoney(investment.initialAmount)} readonly />
          </div>
        </div>
        <div class="field-group">
          <label for="withdrawn-on">Data de resgate <span aria-hidden="true">*</span></label>
          {#if expandedDateEntry}
            <input
              id="withdrawn-on"
              name="withdrawnOn"
              type="text"
              inputmode="text"
              maxlength="14"
              placeholder="-4713-11-24"
              pattern={EXTENDED_ISO_DATE_PATTERN.source}
              required
              bind:value={withdrawnOn}
              disabled={previewing || dateLocked || outcomeUncertain}
              aria-invalid={Boolean(withdrawnOn && !validDate)}
              aria-describedby="withdrawn-on-hint withdrawn-on-error"
              oninput={() => {
                withdrawalDateTouched = true;
                preview = null;
              }}
            />
            <button
              class="date-mode-toggle"
              type="button"
              disabled={!isNativeDateInputValue(withdrawnOn) || previewing || dateLocked || outcomeUncertain}
              onclick={() => {
                expandedDateEntry = false;
              }}
            >Usar calendário para ano 0001–9999</button>
          {:else}
            <input
              id="withdrawn-on"
              name="withdrawnOn"
              type="date"
              min={isNativeDateInputValue(investment.createdOn) ? investment.createdOn : undefined}
              max={today}
              required
              bind:value={withdrawnOn}
              disabled={previewing || dateLocked || outcomeUncertain}
              aria-invalid={Boolean(withdrawnOn && !validDate)}
              aria-describedby="withdrawn-on-hint withdrawn-on-error"
              oninput={() => {
                withdrawalDateTouched = true;
                preview = null;
              }}
            />
            <button
              class="date-mode-toggle"
              type="button"
              disabled={previewing || dateLocked || outcomeUncertain}
              onclick={() => {
                expandedDateEntry = true;
              }}
            >Informar data histórica em ISO</button>
          {/if}
          <p id="withdrawn-on-hint" class="field-hint">
            {expandedDateEntry
              ? `Use ISO local: ano 0000 significa 1 a.C.; anos anteriores usam sinal negativo e anos após 9999 usam sinal positivo. Intervalo: ${formatDate(investment.createdOn)} até ${formatDate(today)}.`
              : `Use o calendário para anos 0001–9999. Para anos anteriores ou posteriores, informe a data em ISO. Intervalo: ${formatDate(investment.createdOn)} até ${formatDate(today)}.`}
          </p>
          <p id="withdrawn-on-error" class="field-error">
            {withdrawnOn && !validDate ? 'A data está fora do período permitido.' : ''}
          </p>
        </div>
        <button
          class="button button-secondary"
          type="submit"
          disabled={previewing || !validDate || outcomeUncertain}
        >
          {previewing ? 'Calculando prévia…' : 'Calcular prévia'}
        </button>
      </form>

      {#if outcomeUncertain}
        <div class="uncertain-panel">
          <h3>Confirme a situação antes de continuar</h3>
          <p>A solicitação pode ter sido processada mesmo sem uma resposta recebida.</p>
          <button class="button button-secondary" type="button" onclick={reconcileWithdrawal} disabled={checking}>
            {checking ? 'Consultando…' : 'Consultar situação'}
          </button>
          {#if recoveryCanBeReleased}
            <button
              class="button button-secondary"
              type="button"
              onclick={releaseAfterActiveReconciliation}
              disabled={checking}
            >
              Liberar nova tentativa após conferir a carteira
            </button>
          {/if}
        </div>
      {/if}
    </section>

    <aside class="withdrawal-aside">
      <div class="aside-icon" aria-hidden="true">↗</div>
      <h2>Resumo do investimento</h2>
      <dl class="summary-list">
        <div><dt>Valor inicial</dt><dd><MoneyValue value={investment.initialAmount} /></dd></div>
        <div><dt>Saldo bruto atual</dt><dd><MoneyValue value={investment.grossBalance} /></dd></div>
        <div><dt>Ganhos acumulados</dt><dd><MoneyValue value={investment.gain} /></dd></div>
      </dl>
      <p class="aside-small">Os valores finais serão calculados pela API para a data escolhida.</p>
    </aside>
  </div>

  {#if preview}
    <section class="content-card preview-card" aria-labelledby="preview-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">REVISÃO DA OPERAÇÃO</p>
          <h2 id="preview-title">Prévia do resgate</h2>
        </div>
        <span class="receipt-date">{formatDate(preview.withdrawnOn)}</span>
      </div>
      <dl class="receipt-grid preview-grid">
        <div><dt>Valor bruto</dt><dd>{formatMoney(preview.grossAmount)}</dd></div>
        <div><dt>Ganhos tributáveis</dt><dd>{formatMoney(preview.gainAmount)}</dd></div>
        <div><dt>Alíquota</dt><dd>{formatTaxRate(preview.taxRate)}</dd></div>
        <div><dt>Imposto retido</dt><dd>{formatMoney(preview.taxAmount)}</dd></div>
        <div class="receipt-net"><dt>Valor líquido previsto</dt><dd>{formatMoney(preview.netAmount)}</dd></div>
      </dl>
      <div class="confirm-row">
        <p>A confirmação registra o resgate integral na data indicada.</p>
        <button
          class="button button-danger"
          type="button"
          onclick={handleConfirm}
          disabled={confirming || outcomeUncertain}
        >
          {confirming ? 'Confirmando…' : 'Confirmar resgate integral'}
        </button>
      </div>
    </section>
  {/if}
{/if}
