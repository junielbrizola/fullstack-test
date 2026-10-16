<script lang="ts">
  let { message }: { message: string } = $props();
</script>

<p class="problem-message" role="alert">{message}</p>
