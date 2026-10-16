<script lang="ts">
  import { goto } from '$app/navigation';
  import { onMount, tick } from 'svelte';
  import { ApiError } from '#lib/api/request.ts';
  import { createInvestment } from '#lib/api/browser.ts';
  import type { CreateInvestmentRequest } from '#lib/api/types.ts';
  import ProblemMessage from '#lib/components/ProblemMessage.svelte';
  import ScreenHeading from '#lib/components/ScreenHeading.svelte';
  import {
    compareIsoLocalDates,
    isNativeDateInputValue,
    isSupportedIsoLocalDate
  } from '#lib/civil-date.ts';
  import { businessToday, normalizeMoneyInput } from '#lib/format.ts';

  const PENDING_CREATION_KEY = 'coderockr.investments.pending-creation.v1';
  const EXTENDED_ISO_DATE_PATTERN = /(?:[0-9]{4}|-(?:000[1-9]|00[1-9][0-9]|0[1-9][0-9]{2}|[1-9][0-9]{3})|\+[1-9][0-9]{4,6})-(?:0[1-9]|1[0-2])-(?:0[1-9]|1[0-9]|2[0-9]|3[01])/;
  const AMOUNT_PATTERN = /^\d{1,17}([.,]\d{1,2})?$/;
  interface PendingCreation {
    idempotencyKey: string;
    payload: CreateInvestmentRequest;
  }

  let ownerName = $state('');
  let createdOn = $state(businessToday());
  let expandedDateEntry = $state(false);
  let initialAmount = $state('');
  let creationIdempotencyKey = $state<string | null>(null);
  let fieldErrors = $state<Record<string, string>>({});
  let formError = $state('');
  let submitting = $state(false);
  let uncertain = $state(false);
  let pendingRecoveryBlocked = $state(false);
  let creationConflict = $state(false);
  let createdInvestmentPath = $state<string | null>(null);

  function isPendingCreation(value: unknown): value is PendingCreation {
    if (typeof value !== 'object' || value === null) return false;
    const candidate = value as Record<string, unknown>;
    if (typeof candidate.idempotencyKey !== 'string' || typeof candidate.payload !== 'object') return false;
    if (candidate.payload === null) return false;
    const payload = candidate.payload as Record<string, unknown>;
    return (
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
        candidate.idempotencyKey
      ) &&
      typeof payload.ownerName === 'string' &&
      payload.ownerName.trim().length > 0 &&
      Array.from(payload.ownerName).length <= 100 &&
      isSupportedIsoLocalDate(payload.createdOn) &&
      typeof payload.initialAmount === 'string' &&
      /^\d{1,17}(\.\d{1,2})?$/.test(payload.initialAmount)
    );
  }

  function persistPendingCreation(pending: PendingCreation): boolean {
    try {
      sessionStorage.setItem(PENDING_CREATION_KEY, JSON.stringify(pending));
      return true;
    } catch {
      return false;
    }
  }

  function clearPendingCreation(): void {
    try {
      sessionStorage.removeItem(PENDING_CREATION_KEY);
    } catch {
      // A stale key remains safe: the API replays the same creation instead of duplicating it.
    }
  }

  function beginNewCreationAfterConflict(): void {
    clearPendingCreation();
    creationIdempotencyKey = null;
    creationConflict = false;
    formError = '';
  }

  function beginNewCreationAfterRecoveryReview(): void {
    clearPendingCreation();
    creationIdempotencyKey = null;
    pendingRecoveryBlocked = false;
    uncertain = false;
    formError = '';
  }

  onMount(() => {
    try {
      const rawPending = sessionStorage.getItem(PENDING_CREATION_KEY);
      if (rawPending === null) return;
      const candidate: unknown = JSON.parse(rawPending);
      if (!isPendingCreation(candidate)) {
        pendingRecoveryBlocked = true;
        uncertain = true;
        formError =
          'A tentativa salva não pode ser interpretada com segurança. Consulte a carteira antes de iniciar outra solicitação.';
        return;
      }

      creationIdempotencyKey = candidate.idempotencyKey;
      ownerName = candidate.payload.ownerName;
      createdOn = candidate.payload.createdOn;
      expandedDateEntry = !isNativeDateInputValue(createdOn);
      initialAmount = candidate.payload.initialAmount;
      uncertain = true;
      if ((compareIsoLocalDates(createdOn, businessToday()) ?? -1) > 0) {
        pendingRecoveryBlocked = true;
        formError =
          'A data da tentativa pendente agora está no futuro. Consulte a carteira antes de liberar outra solicitação.';
      } else {
        formError =
          'Há uma criação sem confirmação nesta sessão. Reenvie a mesma solicitação para recuperar o resultado sem criar outra aplicação.';
      }
    } catch {
      pendingRecoveryBlocked = true;
      uncertain = true;
      formError =
        'Não foi possível recuperar a tentativa pendente desta sessão. Consulte a carteira antes de iniciar outra solicitação.';
    }
  });

  function validate(): string | null {
    fieldErrors = {};
    const normalizedAmount = normalizeMoneyInput(initialAmount);
    if (ownerName.trim().length === 0) fieldErrors.ownerName = 'Informe o nome do titular.';
    if (ownerName.trim().length > 100) fieldErrors.ownerName = 'Use até 100 caracteres.';
    if (!isSupportedIsoLocalDate(createdOn)) {
      fieldErrors.createdOn = 'Informe uma data ISO válida dentro do intervalo de DATE do PostgreSQL.';
    } else if ((compareIsoLocalDates(createdOn, businessToday()) ?? 1) > 0) {
      fieldErrors.createdOn = 'A data não pode estar no futuro.';
    }
    if (normalizedAmount === null) {
      fieldErrors.initialAmount = 'Informe um valor não negativo com até duas casas decimais.';
    }

    return Object.keys(fieldErrors).length > 0 ? 'Revise os campos destacados.' : null;
  }

  async function handleSubmit(event: SubmitEvent): Promise<void> {
    event.preventDefault();
    if (submitting || createdInvestmentPath !== null) return;
    if (pendingRecoveryBlocked) return;

    formError = '';

    if (
      uncertain &&
      creationIdempotencyKey !== null &&
      isSupportedIsoLocalDate(createdOn) &&
      (compareIsoLocalDates(createdOn, businessToday()) ?? -1) > 0
    ) {
      pendingRecoveryBlocked = true;
      formError =
        'A data da tentativa pendente agora está no futuro. A API valida a data antes de consultar a chave; confira a carteira antes de iniciar outra solicitação.';
      return;
    }

    const validationMessage = validate();
    if (validationMessage) {
      formError = validationMessage;
      await tick();
      document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
      return;
    }

    const normalizedAmount = normalizeMoneyInput(initialAmount);
    if (normalizedAmount === null) return;

    const payload: CreateInvestmentRequest = {
      ownerName: ownerName.trim(),
      createdOn,
      initialAmount: normalizedAmount
    };
    let idempotencyKey = creationIdempotencyKey;
    if (idempotencyKey === null) {
      if (typeof crypto.randomUUID !== 'function') {
        formError = 'Este navegador não oferece uma chave segura para criar o investimento.';
        return;
      }
      idempotencyKey = crypto.randomUUID();
    }
    creationIdempotencyKey = idempotencyKey;
    if (!persistPendingCreation({ idempotencyKey, payload })) {
      creationIdempotencyKey = null;
      formError = 'O navegador não permitiu guardar a chave da operação. Nenhuma solicitação foi enviada.';
      return;
    }

    submitting = true;
    try {
      const investment = await createInvestment(payload, idempotencyKey);
      clearPendingCreation();
      creationIdempotencyKey = null;
      const detailPath = '/investments/' + encodeURIComponent(investment.id);
      createdInvestmentPath = detailPath;
      try {
        await goto(detailPath);
      } catch {
        formError = 'O investimento foi criado, mas a navegação automática falhou. Use o link para abrir os detalhes.';
      }
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        uncertain = false;
        creationConflict = true;
        formError = cause.problem.detail;
      } else if (cause instanceof ApiError && cause.status < 500) {
        clearPendingCreation();
        creationIdempotencyKey = null;
        uncertain = false;
        formError = cause.problem.detail;
        for (const fieldError of cause.problem.errors ?? []) {
          if (fieldError.field && fieldError.message) {
            fieldErrors[fieldError.field] = fieldError.message;
          }
        }
        if (Object.keys(fieldErrors).length > 0) {
          await tick();
          document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
        }
      } else {
        uncertain = true;
        formError = cause instanceof ApiError
          ? cause.problem.detail + ' Reenvie os mesmos dados para consultar o resultado idempotente.'
          : 'A resposta da criação não chegou. Reenvie os mesmos dados; a chave idempotente impedirá uma duplicação.';
      }
    } finally {
      submitting = false;
    }
  }

</script>

<svelte:head>
  <title>Novo investimento | Carteira</title>
</svelte:head>

<ScreenHeading
  eyebrow="NOVA APLICAÇÃO"
  title="Criar investimento"
  backHref="/investments"
  backLabel="Voltar para a lista"
>
  <p class="page-lede">Informe os dados da aplicação para adicioná-la à carteira.</p>
</ScreenHeading>

<section class="form-layout">
  <form class="content-card form-card" onsubmit={handleSubmit} aria-busy={submitting} novalidate>
    <div class="form-card-heading">
      <span class="step-number" aria-hidden="true">01</span>
      <div>
        <h2>Dados do investimento</h2>
        <p>Os campos marcados são obrigatórios.</p>
      </div>
    </div>

    {#if formError}
      <ProblemMessage message={formError} />
    {/if}
    {#if pendingRecoveryBlocked}
      <section class="uncertain-panel" aria-labelledby="creation-recovery-blocked-title">
        <h3 id="creation-recovery-blocked-title">A tentativa pendente precisa ser reconciliada</h3>
        <p>
          Não foi possível recuperar a chave e os dados originais, ou a data salva deixou de ser válida.
          Consulte a carteira antes de liberar uma nova solicitação.
        </p>
        <a class="text-link" href="/investments">Consultar investimentos</a>
        <button class="button button-secondary" type="button" onclick={beginNewCreationAfterRecoveryReview}>
          Já conferi a carteira — liberar nova solicitação
        </button>
      </section>
    {:else if uncertain}
      <section class="uncertain-panel" aria-labelledby="creation-reconciliation-title">
        <h3 id="creation-reconciliation-title">A criação ainda não teve confirmação</h3>
        <p>
          A tentativa pode ter sido concluída. Reenvie os mesmos dados para receber o investimento já criado;
          a mesma chave idempotente evita uma duplicação.
        </p>
      </section>
    {/if}
    {#if creationConflict}
      <section class="uncertain-panel" aria-labelledby="creation-conflict-title">
        <h3 id="creation-conflict-title">A chave já está associada a outra criação</h3>
        <p>Confira a carteira antes de iniciar outra operação. A solicitação não será reenviada com uma chave incompatível.</p>
        <a class="text-link" href="/investments">Consultar investimentos</a>
        <button class="button button-secondary" type="button" onclick={beginNewCreationAfterConflict}>
          Já conferi a carteira — iniciar outra solicitação
        </button>
      </section>
    {/if}
    {#if createdInvestmentPath}
      <section class="created-panel" role="status" aria-label="Investimento criado">
        <h3>Investimento criado</h3>
        <a href={createdInvestmentPath}>Abrir detalhes do investimento</a>
      </section>
    {/if}

    <div class="field-group">
      <label for="owner-name">Titular <span aria-hidden="true">*</span></label>
      <input
        id="owner-name"
        name="ownerName"
        type="text"
        autocomplete="name"
        maxlength="100"
        required
        disabled={submitting || uncertain || pendingRecoveryBlocked || creationConflict || createdInvestmentPath !== null}
        bind:value={ownerName}
        aria-invalid={Boolean(fieldErrors.ownerName)}
        aria-describedby="owner-name-hint owner-name-error"
      />
      <p id="owner-name-hint" class="field-hint">Nome da pessoa responsável pelo investimento.</p>
      <p id="owner-name-error" class="field-error">{fieldErrors.ownerName ?? ''}</p>
    </div>

    <div class="field-grid">
      <div class="field-group">
        <label for="created-on">Data de criação <span aria-hidden="true">*</span></label>
        {#if expandedDateEntry}
          <input
            id="created-on"
            name="createdOn"
            type="text"
            inputmode="text"
            maxlength="14"
            placeholder="-4713-11-24"
            pattern={EXTENDED_ISO_DATE_PATTERN.source}
            required
            disabled={submitting || uncertain || pendingRecoveryBlocked || creationConflict || createdInvestmentPath !== null}
            bind:value={createdOn}
            aria-invalid={Boolean(fieldErrors.createdOn)}
            aria-describedby="created-on-hint created-on-error"
          />
          <button
            class="date-mode-toggle"
            type="button"
            disabled={!isNativeDateInputValue(createdOn) || submitting || uncertain || pendingRecoveryBlocked || creationConflict || createdInvestmentPath !== null}
            onclick={() => {
              expandedDateEntry = false;
            }}
          >Usar calendário para ano 0001–9999</button>
        {:else}
          <input
            id="created-on"
            name="createdOn"
            type="date"
            max={businessToday()}
            required
            disabled={submitting || uncertain || pendingRecoveryBlocked || creationConflict || createdInvestmentPath !== null}
            bind:value={createdOn}
            aria-invalid={Boolean(fieldErrors.createdOn)}
            aria-describedby="created-on-hint created-on-error"
          />
          <button
            class="date-mode-toggle"
            type="button"
            disabled={submitting || uncertain || pendingRecoveryBlocked || creationConflict || createdInvestmentPath !== null}
            onclick={() => {
              expandedDateEntry = true;
            }}
          >Informar data histórica em ISO</button>
        {/if}
        <p id="created-on-hint" class="field-hint">
          {expandedDateEntry
            ? 'Hoje ou uma data passada em ISO local. Ano 0000 significa 1 a.C.; anos anteriores usam sinal negativo e anos após 9999 usam sinal positivo.'
            : 'Use o calendário para datas entre os anos 0001 e 9999. Para anos anteriores ou posteriores, informe a data histórica em ISO.'}
        </p>
        <p id="created-on-error" class="field-error">{fieldErrors.createdOn ?? ''}</p>
      </div>

      <div class="field-group">
        <label for="initial-amount">Valor inicial <span aria-hidden="true">*</span></label>
        <div class="money-input">
          <span aria-hidden="true">R$</span>
          <input
            id="initial-amount"
            name="initialAmount"
            type="text"
            inputmode="decimal"
            autocomplete="off"
            maxlength="20"
            pattern={AMOUNT_PATTERN.source}
            placeholder="0,00"
            required
            disabled={submitting || uncertain || pendingRecoveryBlocked || creationConflict || createdInvestmentPath !== null}
            bind:value={initialAmount}
            aria-invalid={Boolean(fieldErrors.initialAmount)}
            aria-describedby="amount-hint amount-error"
          />
        </div>
        <p id="amount-hint" class="field-hint">Use vírgula ou ponto e até duas casas decimais.</p>
        <p id="amount-error" class="field-error">{fieldErrors.initialAmount ?? ''}</p>
      </div>
    </div>

    <div class="form-footer">
      <p class="form-assurance">
        <span class="assurance-icon" aria-hidden="true">✓</span>
        Rendimentos e saldo serão calculados pela API.
      </p>
      <div class="form-actions">
        <a class="button button-quiet" href="/investments">Cancelar</a>
        <button class="button button-primary" type="submit" disabled={submitting || pendingRecoveryBlocked || creationConflict || createdInvestmentPath !== null}>
          {#if submitting}
            Salvando…
          {:else if createdInvestmentPath}
            Criado
          {:else if creationConflict}
            Resolução necessária
          {:else if pendingRecoveryBlocked}
            Reconciliação necessária
          {:else if uncertain}
            Reenviar com a mesma chave
          {:else}
            Criar investimento
          {/if}
        </button>
      </div>
    </div>
  </form>

  <aside class="form-aside">
    <div class="aside-icon" aria-hidden="true">↗</div>
    <h2>Uma carteira clara</h2>
    <p>
      Após criar, você acompanha saldo bruto, ganhos e o histórico de cada investimento em um só lugar.
    </p>
    <div class="aside-rule"></div>
    <p class="aside-small">O valor informado precisa ser zero ou positivo.</p>
  </aside>
</section>
