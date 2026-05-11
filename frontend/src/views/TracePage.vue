<template>
  <PageShell
    eyebrow="Voyage Trace"
    title="Execution Trace"
    subtitle="Inspect the observable execution path behind a TravelPlan or tool-using Agent run."
  >
    <p class="trace-summary-note">
      This view summarizes trace events into interview-friendly Agent stages.
    </p>

    <form class="inline-form" @submit.prevent="loadTrace">
      <input v-model="chatId" placeholder="travel-xxxx or chat-xxxx" />
      <button type="submit" :disabled="loading || !chatId.trim()">Load Trace</button>
    </form>

    <StateBlock v-if="loading" type="loading" title="Loading trace" message="Fetching Agent trace events." />
    <StateBlock v-else-if="error" type="error" title="Trace unavailable" :message="error" />
    <StateBlock
      v-else-if="searched && events.length === 0"
      type="empty"
      title="No events yet"
      message="Run a TravelPlan or tool-using Agent task with this chatId first."
    />

    <div v-if="traceStages.length" class="timeline">
      <article
        v-for="stage in traceStages"
        :key="stage.id"
        :class="['timeline-item', statusClass(stage.status)]"
      >
        <span class="timeline-dot"></span>
        <div>
          <div class="timeline-head">
            <strong>{{ stage.title }}</strong>
            <span :class="['status-pill', statusClass(stage.status)]">{{ stage.status }}</span>
          </div>
          <p>{{ stage.description }}</p>
          <small>{{ stage.timestamp }}</small>
          <div v-if="stage.metadata.length" class="metadata-grid">
            <div v-for="item in stage.metadata" :key="item.key">
              <dt>{{ item.key }}</dt>
              <dd>{{ item.value }}</dd>
            </div>
          </div>
        </div>
      </article>
    </div>

    <details v-if="events.length" class="raw-trace-panel">
      <summary>Raw trace events</summary>
      <div class="timeline raw-trace-list">
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
    </details>
  </PageShell>
</template>

<script>
import api from '../api'
import PageShell from '../components/common/PageShell.vue'
import StateBlock from '../components/common/StateBlock.vue'

const STAGE_DEFINITIONS = [
  {
    id: 'intent',
    step: 'USER_INTENT_RECOGNITION',
    title: 'Intent recognized',
    fallback: 'The Agent identified the travel or tool task and captured known constraints.'
  },
  {
    id: 'skills',
    step: 'SKILL_LOADING',
    title: 'Skills loaded',
    fallback: 'The Agent selected bounded skills or rules for the request.'
  },
  {
    id: 'itinerary',
    step: 'ITINERARY_GENERATION',
    title: 'Itinerary generated',
    fallback: 'The Agent produced the structured itinerary draft.'
  },
  {
    id: 'budget',
    step: 'BUDGET_CHECK',
    title: 'Budget checked',
    fallback: 'The Agent checked budget structure and uncertainty.'
  },
  {
    id: 'risks',
    step: 'RISK_CHECK',
    title: 'Risks checked',
    fallback: 'The Agent reviewed risk reminders, missing inputs, and guardrails.'
  },
  {
    id: 'final',
    step: 'REPORT_GENERATION',
    title: 'Final response composed',
    fallback: 'The structured TravelPlan is ready for UI rendering.'
  }
]

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
  computed: {
    traceStages() {
      return STAGE_DEFINITIONS
        .map((definition) => this.buildStage(definition))
        .filter(Boolean)
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
    buildStage(definition) {
      const candidates = this.events.filter((event) => event.step === definition.step)
      if (!candidates.length) return null
      const event = this.pickBestEvent(candidates)
      return {
        id: definition.id,
        title: definition.title,
        status: event.status || 'COMPLETED',
        description: this.stageDescription(definition, event),
        timestamp: event.timestamp,
        metadata: this.stageMetadata(definition, event)
      }
    },
    pickBestEvent(events) {
      return [...events].sort((a, b) => {
        const statusDelta = this.statusScore(b.status) - this.statusScore(a.status)
        if (statusDelta !== 0) return statusDelta
        const metadataDelta = this.metadataSize(b) - this.metadataSize(a)
        if (metadataDelta !== 0) return metadataDelta
        return this.timeValue(b.timestamp) - this.timeValue(a.timestamp)
      })[0]
    },
    stageDescription(definition, event) {
      if (definition.id === 'final') return definition.fallback
      return event.message || definition.fallback
    },
    stageMetadata(definition, event) {
      const metadata = event.metadata || {}
      const items = []
      const add = (key, value) => {
        if (value === undefined || value === null || value === '') return
        items.push({ key, value: this.renderValue(value) })
      }

      if (definition.id === 'intent') {
        add('destination', metadata.destination)
        add('departure', metadata.departure)
        add('days', metadata.days)
        add('missing', metadata.missingFields)
        add('taskType', metadata.taskType)
      } else if (definition.id === 'skills') {
        add('loadedSkills', metadata.loadedSkills)
      } else if (definition.id === 'itinerary') {
        add('days', metadata.days)
        add('summary', metadata.summary)
      } else if (definition.id === 'budget') {
        add('currency', metadata.currency)
        add('estimate', metadata.estimate)
        add('hasBudget', metadata.hasBudget)
        add('travelerCount', this.intentMissingTravelers() ? 'unknown' : metadata.travelers)
      } else if (definition.id === 'risks') {
        add('riskCount', metadata.riskCount)
      } else if (definition.id === 'final') {
        add('renderedOutput', 'Structured TravelPlan')
      }

      if (!items.length && this.hasMetadata(event)) {
        return Object.entries(metadata).map(([key, value]) => ({ key, value: this.renderValue(value) }))
      }
      return items
    },
    intentMissingTravelers() {
      return this.events.some((event) => {
        const missing = event.metadata?.missingFields
        return Array.isArray(missing) ? missing.includes('travelers') : String(missing || '').includes('travelers')
      })
    },
    statusScore(status) {
      const value = String(status || '').toUpperCase()
      if (value === 'COMPLETED') return 3
      if (value === 'FAILED') return 2
      if (value === 'STARTED') return 1
      return 0
    },
    metadataSize(event) {
      return event.metadata ? Object.keys(event.metadata).length : 0
    },
    timeValue(timestamp) {
      const value = Date.parse(timestamp || '')
      return Number.isNaN(value) ? 0 : value
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
