<template>
  <PageShell
    eyebrow="Voyage Trace"
    title="Execution Trace"
    subtitle="Enter a chatId and inspect steps, statuses, metadata, and timestamps."
  >
    <form class="inline-form" @submit.prevent="loadTrace">
      <input v-model="chatId" placeholder="travel-xxxx or chat-xxxx" />
      <button type="submit" :disabled="loading || !chatId.trim()">Load Trace</button>
    </form>

    <StateBlock v-if="loading" type="loading" title="Loading trace" message="Fetching Agent trace events." />
    <StateBlock v-else-if="error" type="error" title="Trace unavailable" :message="error" />
    <StateBlock v-else-if="searched && events.length === 0" type="empty" title="No events yet" message="Run a travel plan or chat request with this chatId first." />

    <div v-if="events.length" class="timeline">
      <article
        v-for="event in events"
        :key="event.traceId || `${event.step}-${event.timestamp}`"
        :class="['timeline-item', statusClass(event.status)]"
      >
        <span class="timeline-dot"></span>
        <div>
          <div class="timeline-head">
            <strong>{{ formatStep(event.step) }}</strong>
            <span :class="['status-pill', statusClass(event.status)]">{{ event.status }}</span>
          </div>
          <p>{{ event.message }}</p>
          <small>{{ event.timestamp }}</small>
          <div v-if="hasMetadata(event)" class="metadata-grid">
            <div v-for="(value, key) in event.metadata" :key="key">
              <dt>{{ key }}</dt>
              <dd>{{ renderValue(value) }}</dd>
            </div>
          </div>
        </div>
      </article>
    </div>
  </PageShell>
</template>

<script>
import api from '../api'
import PageShell from '../components/common/PageShell.vue'
import StateBlock from '../components/common/StateBlock.vue'

export default {
  name: 'TracePage',
  components: { PageShell, StateBlock },
  data() {
    return {
      chatId: '',
      events: [],
      loading: false,
      searched: false,
      error: ''
    }
  },
  mounted() {
    const queryChatId = this.$route.query.chatId
    if (typeof queryChatId === 'string' && queryChatId.trim()) {
      this.chatId = queryChatId.trim()
      this.loadTrace()
    }
  },
  watch: {
    '$route.query.chatId'(value) {
      if (typeof value === 'string' && value.trim() && value.trim() !== this.chatId) {
        this.chatId = value.trim()
        this.loadTrace()
      }
    }
  },
  methods: {
    async loadTrace() {
      this.loading = true
      this.error = ''
      this.searched = true
      try {
        const { data } = await api.get(`/travel/trace/${encodeURIComponent(this.chatId.trim())}`)
        this.events = Array.isArray(data) ? data : []
      } catch (err) {
        this.events = []
        this.error = 'Could not load trace. Check the chatId and backend service.'
      } finally {
        this.loading = false
      }
    },
    formatStep(step) {
      return String(step || '').replaceAll('_', ' ')
    },
    statusClass(status) {
      return String(status || '').toLowerCase()
    },
    hasMetadata(event) {
      return event.metadata && Object.keys(event.metadata).length > 0
    },
    renderValue(value) {
      if (Array.isArray(value)) return value.join(', ')
      if (value && typeof value === 'object') return JSON.stringify(value)
      return String(value)
    }
  }
}
</script>
