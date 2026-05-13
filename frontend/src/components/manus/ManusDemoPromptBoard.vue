<template>
  <div class="tool-prompt-board" aria-label="SyManus demo tasks">
    <section class="prompt-group stable-demo">
      <div class="prompt-group-head">
        <strong>Stable Engineering Demos</strong>
        <span>Local and repeatable</span>
      </div>
      <p class="prompt-copy">
        Fixed backend tasks for project quality checks, targeted tests, runtime verification, portfolio artifacts, and a Wayfinder trace card.
      </p>
      <div class="demo-card-grid">
        <button
          v-for="example in stableDemoExamples"
          :key="example.label"
          class="demo-tool-button"
          type="button"
          :disabled="isStreaming"
          @click="$emit('stable-demo', example)"
        >
          <strong>{{ example.label }}</strong>
          <small>{{ example.description }}</small>
        </button>
      </div>
    </section>

    <section class="prompt-group live">
      <div class="prompt-group-head">
        <strong>{{ liveTaskHeading }}</strong>
        <span>{{ liveTaskSubheading }}</span>
      </div>
      <p class="prompt-copy">
        {{ liveTaskCopy }}
      </p>
      <div class="demo-card-grid">
        <button
          v-for="example in liveTaskExamples"
          :key="example.label"
          class="demo-tool-button live-example-button"
          type="button"
          :disabled="isStreaming"
          @click="$emit('live-task', example)"
        >
          <strong>{{ example.label }}</strong>
          <small>{{ liveExampleDescription(example) }}</small>
        </button>
      </div>
    </section>
  </div>
</template>

<script>
export default {
  name: 'ManusDemoPromptBoard',
  props: {
    stableDemoExamples: { type: Array, default: () => [] },
    liveTaskExamples: { type: Array, default: () => [] },
    isStreaming: { type: Boolean, default: false },
    publicDemoMode: { type: Boolean, default: true },
    liveTaskHeading: { type: String, required: true },
    liveTaskSubheading: { type: String, required: true },
    liveTaskCopy: { type: String, required: true }
  },
  emits: ['stable-demo', 'live-task'],
  methods: {
    liveExampleDescription(example) {
      if (this.publicDemoMode) return `Fill example prompt: ${example.description}`
      return example.description
    }
  }
}
</script>
