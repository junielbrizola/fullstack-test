<script lang="ts">
  let { status }: { status: 'ACTIVE' | 'WITHDRAWN' } = $props();

  const label = $derived(status === 'ACTIVE' ? 'Investido' : 'Resgatado');
</script>

<span class:withdrawn={status === 'WITHDRAWN'} class="status-badge">
  <span class="status-dot" aria-hidden="true"></span>
  {label}
</span>
