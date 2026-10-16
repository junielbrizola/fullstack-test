<script lang="ts">
  import { page } from '$app/state';

  const heading = $derived(page.status === 404 ? 'Página não encontrada' : 'Não foi possível carregar');
  const detail = $derived(
    page.status === 404
      ? 'O endereço pode estar incorreto ou o investimento não existe.'
      : 'A API pode estar indisponível. Tente novamente em alguns instantes.'
  );
</script>

<svelte:head>
  <title>{heading} | Investimentos</title>
</svelte:head>

<section class="state-panel state-panel-centered" aria-labelledby="error-title">
  <div class="state-symbol state-symbol-error" aria-hidden="true">!</div>
  <p class="eyebrow">Solicitação não concluída</p>
  <h1 id="error-title">{heading}</h1>
  <p class="state-description">{detail}</p>
  <a class="button button-primary" href="/investments">Voltar para investimentos</a>
</section>
