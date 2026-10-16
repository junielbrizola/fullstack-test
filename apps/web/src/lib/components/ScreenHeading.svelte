<script lang="ts">
  import type { Snippet } from 'svelte';

  type Props = {
    title: string;
    titleId?: string;
    eyebrow?: string;
    variant?: 'page' | 'card';
    backHref?: string;
    backLabel?: string;
    children?: Snippet;
    actions?: Snippet;
  };

  let {
    title,
    titleId,
    eyebrow,
    variant = 'page',
    backHref,
    backLabel = 'Voltar para a lista',
    children,
    actions
  }: Props = $props();
</script>

{#if backHref}
  <a class="back-link" href={backHref}><span aria-hidden="true">←</span> {backLabel}</a>
{/if}

<header class="page-heading" class:page-heading-card={variant === 'card'}>
  <div class="page-heading-copy">
    {#if eyebrow}<p class="eyebrow">{eyebrow}</p>{/if}
    <h1 id={titleId}>{title}</h1>
    {@render children?.()}
  </div>
  {#if actions}
    <div class="page-heading-actions">{@render actions()}</div>
  {/if}
</header>
