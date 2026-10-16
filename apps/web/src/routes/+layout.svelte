<script lang="ts">
  import '../app.css';
  import { navigating } from '$app/state';
  import type { Snippet } from 'svelte';

  let { children }: { children: Snippet } = $props();
  let isNavigating = $derived(navigating !== null && navigating.to !== null);
</script>

<a class="skip-link" href="#main-content">Pular para o conteúdo</a>

<div class="app-shell">
  <header class="topbar">
    <a class="brand-mark-link" href="/investments" aria-label="Coderockr, página de investimentos">
      <img src="/coderockr-mark.svg" alt="" width="20" height="48" />
    </a>
  </header>

  <main id="main-content" class="main-content" aria-busy={isNavigating}>
    <p class="visually-hidden" role="status" aria-live="polite">
      {isNavigating ? 'Carregando página…' : ''}
    </p>
    {@render children()}
  </main>
</div>
