<script lang="ts">
  import { formatMoney } from '#lib/format.ts';
  import type { Money, Principal } from '#lib/api/types.ts';

  let { value, emphasis = false }: { value: Money | Principal; emphasis?: boolean } = $props();
</script>

<span class:emphasis class="money-value">{formatMoney(value)}</span>
