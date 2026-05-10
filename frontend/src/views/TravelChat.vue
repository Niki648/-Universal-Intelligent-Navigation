<template>
  <PageShell
    eyebrow="Travel Cabin"
    title="Travel Agent"
    subtitle="Chat with the travel Agent, or request a structured TravelPlan for UI-ready cards."
  >
    <div class="travel-agent-layout">
      <section class="travel-chat-column">
        <h2>Streaming Chat</h2>
        <ChatWindow title="Travel Agent" sse-path="/travel/chat/stream" />
      </section>

      <section class="structured-plan-column">
        <h2>Structured Plan</h2>
        <form class="prompt-form" @submit.prevent="generatePlan">
          <textarea
            v-model="planMessage"
            rows="5"
            placeholder="Example: Plan a relaxed 5-day family trip to Kyoto with a 15000 CNY budget."
          ></textarea>
          <button type="submit" :disabled="planLoading || !planMessage.trim()">
            {{ planLoading ? 'Planning...' : 'Generate TravelPlan' }}
          </button>
        </form>

        <StateBlock
          v-if="planLoading"
          type="loading"
          title="Planning voyage"
          message="The structured TravelPlan can take 30-90 seconds when the live model is composing itinerary, budget, risks, and loaded Skills."
        />

        <StateBlock v-if="planError" type="error" title="Plan request failed" :message="planError" />

        <div v-if="plan" class="plan-result">
          <StarCard title="Summary" :description="plan.summary">
            <template #meta>{{ plan.destination || 'Destination TBD' }}</template>
            <dl class="profile-facts">
              <div>
                <dt>Days</dt>
                <dd>{{ plan.days || 'TBD' }}</dd>
              </div>
              <div>
                <dt>Travelers</dt>
                <dd>{{ plan.travelers || 'TBD' }}</dd>
              </div>
              <div v-if="plan.departure">
                <dt>Departure</dt>
                <dd>{{ plan.departure }}</dd>
              </div>
            </dl>
          </StarCard>

          <StarCard v-if="plan.budget" title="Budget" :description="plan.budget.note || plan.budget.uncertaintyNote || ''">
            <template #meta>{{ plan.budget.total || 'Estimate' }} {{ plan.budget.currency || '' }}</template>
            <ul class="feature-list">
              <li v-for="item in budgetItems" :key="item">{{ item }}</li>
            </ul>
          </StarCard>

          <StarCard v-if="plan.itineraryDays?.length" title="Itinerary Days">
            <div class="compact-list">
              <article v-for="day in plan.itineraryDays" :key="day.day || day.title">
                <strong>Day {{ day.day || '?' }} {{ day.title || '' }}</strong>
                <p>{{ day.summary || day.theme || day.description || renderObject(day) }}</p>
              </article>
            </div>
          </StarCard>

          <StarCard v-if="plan.risks?.length" title="Risks and Guardrails">
            <ul class="feature-list">
              <li v-for="risk in plan.risks" :key="risk">{{ risk }}</li>
            </ul>
          </StarCard>

          <StarCard v-if="plan.loadedSkills?.length" title="Loaded Skills">
            <TagList :items="plan.loadedSkills" />
          </StarCard>

          <div class="card-actions">
            <router-link :to="{ path: '/trace', query: { chatId: planChatId } }">
              View this Agent voyage trace
            </router-link>
          </div>
        </div>
      </section>
    </div>
  </PageShell>
</template>

<script>
import api from '../api'
import ChatWindow from '../components/ChatWindow.vue'
import PageShell from '../components/common/PageShell.vue'
import StarCard from '../components/common/StarCard.vue'
import TagList from '../components/common/TagList.vue'
import StateBlock from '../components/common/StateBlock.vue'

export default {
  name: 'TravelChat',
  components: { ChatWindow, PageShell, StarCard, TagList, StateBlock },
  data() {
    return {
      planMessage: 'Plan a relaxed 5-day family trip to Kyoto with a 15000 CNY budget.',
      plan: null,
      planChatId: '',
      planLoading: false,
      planError: ''
    }
  },
  computed: {
    budgetItems() {
      const items = this.plan?.budget?.items || this.plan?.budget?.breakdown || this.plan?.budget?.itemized || []
      if (!Array.isArray(items)) return []
      return items.map((item) => {
        if (typeof item === 'string') return item
        return `${item.name || item.category || 'Item'}: ${item.amount || item.estimate || ''} ${item.currency || ''}`.trim()
      })
    }
  },
  methods: {
    async generatePlan() {
      this.planLoading = true
      this.planError = ''
      this.plan = null
      try {
        const chatId = `travel-plan-${Date.now()}`
        this.planChatId = chatId
        const { data } = await api.post(
          '/travel/plan',
          {
            message: this.planMessage,
            chatId
          },
          {
            timeout: 120000
          }
        )
        this.plan = data
      } catch (err) {
        this.planError = this.planErrorMessage(err)
      } finally {
        this.planLoading = false
      }
    },
    planErrorMessage(err) {
      if (err?.code === 'ECONNABORTED') {
        return 'The live model is still taking too long. Try a shorter request, enable Demo Mode for interviews, or check the backend logs for slow model responses.'
      }
      if (err?.response?.data?.message) {
        return err.response.data.message
      }
      return 'Could not generate a structured TravelPlan. Check backend and model configuration.'
    },
    renderObject(value) {
      return JSON.stringify(value)
    }
  }
}
</script>
