<template>
  <PageShell
    eyebrow="Travel Cabin"
    title="Travel Agent"
    subtitle="Chat with the travel Agent, or request a structured TravelPlan for UI-ready cards."
  >
    <div class="travel-agent-layout">
      <section class="travel-chat-column">
        <h2>Streaming Chat</h2>
        <ChatWindow
          title="Travel Agent"
          sse-path="/travel/chat/stream"
          storage-key="wayfinder.travel.chat"
          clear-label="New Chat"
          :local-responder="travelLocalResponder"
          :hidden-line-prefixes="['PLAN_DRAFT:']"
          @submitted="handleChatSubmitted"
          @completed="handleChatCompleted"
          @cleared="handleChatCleared"
        />
        <p v-if="planLoading" class="draft-notice">{{ planLoadingChatNotice }}</p>
        <div v-if="chatDraft" class="chat-draft-action">
          <div>
            <strong>{{ chatDraftReadyTitle }}</strong>
            <p>{{ chatDraft }}</p>
          </div>
          <button type="button" :disabled="planLoading" @click="generatePlanFromChatDraft">
            {{ chatDraftActionLabel }}
          </button>
        </div>
      </section>

      <section class="structured-plan-column">
        <h2>Structured Plan</h2>
        <form class="prompt-form" @submit.prevent="generatePlan">
          <textarea
            v-model="planMessage"
            rows="5"
            placeholder="Example: Plan a relaxed 5-day family trip from Shanghai to Kyoto with a 15000 CNY budget."
          ></textarea>
          <div class="form-actions">
            <button type="submit" :disabled="planLoading || !planMessage.trim()">
              {{ planLoading ? 'Planning...' : 'Generate TravelPlan' }}
            </button>
            <button
              v-if="chatDraft"
              type="button"
              class="secondary-button"
              :disabled="planLoading"
              @click="generatePlanFromChatDraft"
            >
              {{ chatDraftActionLabel }}
            </button>
            <button type="button" class="secondary-button" :disabled="planLoading" @click="clearPlanSession">
              New Demo
            </button>
          </div>
          <p v-if="chatDraftNotice" class="draft-notice">{{ chatDraftNotice }}</p>
        </form>

        <StateBlock
          v-if="planLoading"
          type="loading"
          title="Planning voyage"
          :message="planLoadingMessage"
        />

        <StateBlock v-if="planError" type="error" title="Plan request failed" :message="planError" />

        <div v-if="plan" class="plan-result">
          <StarCard title="Summary" :description="displaySummary">
            <template #meta>{{ plan.destination || 'Destination TBD' }}</template>
            <dl class="profile-facts">
              <div v-if="displayDays">
                <dt>Days</dt>
                <dd>{{ displayDays }}</dd>
              </div>
              <div v-if="shouldShowTravelers">
                <dt>Travelers</dt>
                <dd>{{ plan.travelers || 'TBD' }}</dd>
              </div>
              <div v-if="displayDeparture">
                <dt>Departure</dt>
                <dd>{{ displayDeparture }}</dd>
              </div>
            </dl>
          </StarCard>

          <StarCard v-if="plan.budget" title="Budget" :description="displayBudgetNote">
            <template #meta>{{ plan.budget.total || 'Estimate' }} {{ plan.budget.currency || '' }}</template>
            <div class="budget-list">
              <article v-for="item in budgetItems" :key="item.key" class="budget-item">
                <strong>{{ item.label }}</strong>
                <p v-if="item.note">{{ item.note }}</p>
              </article>
            </div>
          </StarCard>

          <StarCard v-if="plan.itineraryDays?.length" title="Itinerary Days">
            <div class="itinerary-list">
              <article v-for="day in plan.itineraryDays" :key="day.day || day.title">
                <details open>
                  <summary>
                    <strong>Day {{ day.day || '?' }} {{ day.theme || day.title || '' }}</strong>
                    <span v-if="day.pace">{{ day.pace }}</span>
                  </summary>
                  <div class="day-details">
                    <p v-if="day.summary || day.description">{{ day.summary || day.description }}</p>
                    <p v-if="day.transport"><strong>Transport:</strong> {{ day.transport }}</p>
                    <div v-if="day.activities?.length" class="activity-list">
                      <article v-for="(activity, activityIndex) in day.activities" :key="`${day.day || 'day'}-${activityIndex}`">
                        <div class="activity-heading">
                          <span v-if="activity.time">{{ activity.time }}</span>
                          <strong>{{ activity.title || 'Activity' }}</strong>
                        </div>
                        <p v-if="activity.description">{{ activity.description }}</p>
                        <p v-if="activity.area || activity.costLevel" class="activity-meta">
                          {{ [activity.area, activity.costLevel && `Cost: ${activity.costLevel}`].filter(Boolean).join(' · ') }}
                        </p>
                        <ul v-if="activity.tips?.length" class="mini-list">
                          <li v-for="tip in activity.tips" :key="tip">{{ tip }}</li>
                        </ul>
                      </article>
                    </div>
                    <p v-if="day.meals?.length"><strong>Meals:</strong> {{ day.meals.join('；') }}</p>
                    <p v-if="day.reminders?.length"><strong>Reminders:</strong> {{ day.reminders.join('；') }}</p>
                    <p v-if="day.accommodation"><strong>Accommodation:</strong> {{ day.accommodation }}</p>
                  </div>
                </details>
              </article>
            </div>
          </StarCard>

          <StarCard v-if="plan.transportation?.length" title="Transportation">
            <ul class="feature-list">
              <li v-for="item in plan.transportation" :key="item">{{ item }}</li>
            </ul>
          </StarCard>

          <StarCard v-if="plan.accommodation?.length" title="Accommodation">
            <ul class="feature-list">
              <li v-for="item in plan.accommodation" :key="item">{{ item }}</li>
            </ul>
          </StarCard>

          <StarCard v-if="displayRisks.length" title="Risks and Guardrails">
            <ul class="feature-list">
              <li v-for="risk in displayRisks" :key="risk">{{ risk }}</li>
            </ul>
          </StarCard>

          <StarCard v-if="plan.alternatives?.length" title="Alternatives">
            <ul class="feature-list">
              <li v-for="item in plan.alternatives" :key="item">{{ item }}</li>
            </ul>
          </StarCard>

          <StarCard v-if="plan.loadedSkills?.length" title="Loaded Skills">
            <TagList :items="plan.loadedSkills" />
          </StarCard>

          <StarCard title="Plan Quality Score" description="Scores this generated TravelPlan against your current request.">
            <template #meta>Current Request</template>
            <div class="form-actions">
              <button type="button" :disabled="scoreLoading" @click="scoreCurrentPlan">
                {{ scoreLoading ? 'Scoring...' : 'Score Current Plan' }}
              </button>
            </div>
            <p class="draft-notice">
              Scores this generated TravelPlan against your current request.
            </p>
            <p v-if="scoreError" class="draft-notice">{{ scoreError }}</p>
            <div v-if="scoreResult" class="eval-run-panel compact-eval-panel">
              <div class="eval-score-row">
                <div>
                  <p class="area-kicker">Scored Against Current Request</p>
                  <h2>{{ scoreResult.result.caseName }}</h2>
                  <span :class="['score-chip', scoreResult.result.passed ? 'pass' : 'fail']">
                    {{ scoreResult.result.passed ? 'PASS' : 'FAIL' }}
                    {{ scoreResult.result.score }}/{{ scoreResult.result.maxScore }}
                  </span>
                </div>
                <div class="eval-input-block">
                  <strong>Current Request</strong>
                  <p>{{ scoreResult.input }}</p>
                </div>
              </div>
              <div class="eval-rule-grid">
                <article
                  v-for="rule in scoreResult.result.rules"
                  :key="rule.rule"
                  :class="['rule-result-sample', rule.passed ? 'passed' : 'failed']"
                >
                  <strong>{{ rule.rule }}</strong>
                  <span :class="['score-chip', rule.passed ? 'pass' : 'fail']">
                    {{ rule.passed ? 'PASS' : 'FAIL' }} {{ rule.score }}/{{ rule.maxScore }}
                  </span>
                  <p>{{ rule.message }}</p>
                </article>
              </div>
            </div>
          </StarCard>

          <div class="card-actions">
            <router-link :to="{ path: '/trace', query: { chatId: planChatId } }">
              {{ traceLinkLabel }}
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

const DEFAULT_PLAN_MESSAGE = 'Plan a relaxed 5-day family trip from Shanghai to Kyoto with a 15000 CNY budget.'
const DEFAULT_DEMO_STATUS = {
  demoMode: true,
  liveManusAvailable: false,
  searchAvailable: false,
  imageSearchAvailable: false
}

export default {
  name: 'TravelChat',
  components: { ChatWindow, PageShell, StarCard, TagList, StateBlock },
  data() {
    return {
      planMessage: DEFAULT_PLAN_MESSAGE,
      plan: null,
      planChatId: '',
      planLoading: false,
      planError: '',
      chatDraft: '',
      chatDraftNotice: '',
      chatDraftLanguage: 'en',
      activePlanStartedAt: '',
      planSessionPoller: null,
      scoreLoading: false,
      scoreError: '',
      scoreResult: null,
      demoStatus: { ...DEFAULT_DEMO_STATUS }
    }
  },
  computed: {
    budgetItems() {
      const items = this.plan?.budget?.items || this.plan?.budget?.breakdown || this.plan?.budget?.itemized || []
      if (!Array.isArray(items)) return []
      return items.map((item, index) => {
        if (typeof item === 'string') return { key: item, label: item, note: '' }
        const amount = item.amount || item.estimate || ''
        const currency = item.currency || this.plan?.budget?.currency || ''
        const label = `${item.name || item.category || 'Item'}: ${amount} ${currency}`.trim()
        return {
          key: `${label}-${index}`,
          label,
          note: item.note || item.description || ''
        }
      })
    },
    displaySummary() {
      if (this.hasExplicitTravelers(this.planMessage)) return this.plan?.summary || ''
      return this.stripInventedTravelerAssumption(this.plan?.summary || '')
    },
    displayBudgetNote() {
      const note = this.plan?.budget?.note || this.plan?.budget?.uncertaintyNote || ''
      if (this.hasExplicitTravelers(this.planMessage)) return note
      return this.stripInventedTravelerAssumption(note)
    },
    displayRisks() {
      const risks = Array.isArray(this.plan?.risks) ? this.plan.risks : []
      return risks
        .map((risk) => this.normalizeMissingFieldRisk(risk))
        .filter(Boolean)
        .map((risk) => this.hasExplicitTravelers(this.planMessage) ? risk : this.stripInventedTravelerAssumption(risk))
        .filter(Boolean)
    },
    shouldShowTravelers() {
      if (!this.plan?.travelers) return false
      return this.hasExplicitTravelers(this.planMessage)
    },
    displayDays() {
      return this.extractDays(this.planMessage) || this.plan?.days || ''
    },
    displayDeparture() {
      return this.extractDeparture(this.planMessage) || this.plan?.departure || ''
    },
    requestMessage() {
      return this.planMessage.trim()
    },
    chatDraftActionLabel() {
      return this.chatDraftLanguage === 'zh' ? '生成结构化计划' : 'Generate Structured Plan'
    },
    chatDraftReadyTitle() {
      return this.chatDraftLanguage === 'zh' ? '结构化计划草案已就绪' : 'TravelPlan draft ready'
    },
    planLoadingChatNotice() {
      const language = this.preferredLanguage(this.chatDraft || this.planMessage)
      return language === 'zh'
        ? '结构化计划正在生成中，聊天回复可能稍慢。'
        : 'Structured plan is generating; chat replies may be slower.'
    },
    planLoadingMessage() {
      if (this.demoStatus.demoMode) {
        return 'Demo mode is returning the frozen TravelPlan fixture and its matching trace.'
      }
      return 'The structured TravelPlan can take 30-90 seconds when the live model is composing itinerary, budget, risks, and loaded Skills.'
    },
    traceLinkLabel() {
      if (this.demoStatus.demoMode) return 'View demo voyage trace'
      if (this.demoStatus.liveManusAvailable) return 'View this live Agent trace'
      return 'View trace fixture'
    }
  },
  mounted() {
    this.restorePlanSession()
    this.fetchDemoStatus()
  },
  beforeUnmount() {
    this.stopPlanSessionPolling()
  },
  methods: {
    async fetchDemoStatus() {
      try {
        const { data } = await api.get('/travel/demo-status')
        this.demoStatus = { ...DEFAULT_DEMO_STATUS, ...data }
      } catch (error) {
        this.demoStatus = { ...DEFAULT_DEMO_STATUS }
      }
    },
    async generatePlan() {
      this.planLoading = true
      this.planError = ''
      this.plan = null
      this.planChatId = `travel-plan-${Date.now()}`
      this.activePlanStartedAt = new Date().toISOString()
      this.savePlanSession()
      try {
        const { data } = await api.post(
          '/travel/plan',
          {
            message: this.requestMessage,
            chatId: this.planChatId
          },
          {
            timeout: 120000
          }
        )
        this.plan = data
        this.scoreResult = null
        this.scoreError = ''
        this.savePlanSession()
      } catch (err) {
        this.planError = this.planErrorMessage(err)
        this.savePlanSession()
      } finally {
        this.planLoading = false
        this.activePlanStartedAt = ''
        this.savePlanSession()
      }
    },
    clearPlanSession() {
      sessionStorage.removeItem('wayfinder.travel.plan')
      this.stopPlanSessionPolling()
      this.planMessage = DEFAULT_PLAN_MESSAGE
      this.plan = null
      this.planChatId = ''
      this.planLoading = false
      this.planError = ''
      this.chatDraft = ''
      this.chatDraftNotice = ''
      this.activePlanStartedAt = ''
      this.scoreLoading = false
      this.scoreError = ''
      this.scoreResult = null
    },
    restorePlanSession() {
      try {
        const raw = sessionStorage.getItem('wayfinder.travel.plan')
        if (!raw) return
        const saved = JSON.parse(raw)
        this.planMessage = saved.lastPrompt || saved.planMessage || this.planMessage
        this.plan = saved.structuredPlan || saved.plan || null
        this.planChatId = saved.chatId || ''
        this.planError = saved.planError || ''
        this.chatDraft = saved.chatDraft || ''
        this.chatDraftNotice = saved.chatDraftNotice || ''
        this.chatDraftLanguage = saved.chatDraftLanguage || this.preferredLanguage(this.chatDraft || this.planMessage)
        this.scoreResult = saved.scoreResult || null
        this.scoreError = saved.scoreError || ''
        this.activePlanStartedAt = saved.activePlanStartedAt || ''
        this.planLoading = saved.taskStatus === 'Planning' && !this.plan && !this.planError
        if (this.planLoading) this.startPlanSessionPolling()
      } catch (error) {
        console.warn('Could not restore travel plan session.', error)
      }
    },
    savePlanSession() {
      sessionStorage.setItem('wayfinder.travel.plan', JSON.stringify({
        chatId: this.planChatId,
        messages: [],
        structuredPlan: this.plan,
        generatedPlan: this.plan,
        taskStatus: this.planLoading ? 'Planning' : this.planError ? 'Failed' : this.plan ? 'Completed' : 'Idle',
        lastPrompt: this.planMessage,
        planError: this.planError,
        chatDraft: this.chatDraft,
        chatDraftNotice: this.chatDraftNotice,
        chatDraftLanguage: this.chatDraftLanguage,
        scoreResult: this.scoreResult,
        scoreError: this.scoreError,
        activePlanStartedAt: this.activePlanStartedAt
      }))
    },
    startPlanSessionPolling() {
      this.stopPlanSessionPolling()
      this.planSessionPoller = window.setInterval(() => {
        this.refreshPlanSessionFromStorage()
      }, 1000)
    },
    stopPlanSessionPolling() {
      if (this.planSessionPoller) {
        window.clearInterval(this.planSessionPoller)
        this.planSessionPoller = null
      }
    },
    refreshPlanSessionFromStorage() {
      try {
        const raw = sessionStorage.getItem('wayfinder.travel.plan')
        if (!raw) return
        const saved = JSON.parse(raw)
        this.planMessage = saved.lastPrompt || saved.planMessage || this.planMessage
        this.planChatId = saved.chatId || this.planChatId
        this.chatDraft = saved.chatDraft || this.chatDraft
        this.chatDraftNotice = saved.chatDraftNotice || this.chatDraftNotice
        this.chatDraftLanguage = saved.chatDraftLanguage || this.chatDraftLanguage
        this.activePlanStartedAt = saved.activePlanStartedAt || this.activePlanStartedAt
        if (saved.structuredPlan || saved.plan || saved.planError || saved.taskStatus !== 'Planning') {
          this.plan = saved.structuredPlan || saved.plan || this.plan
          this.planError = saved.planError || ''
          this.scoreResult = saved.scoreResult || this.scoreResult
          this.scoreError = saved.scoreError || ''
          this.planLoading = false
          this.activePlanStartedAt = ''
          this.stopPlanSessionPolling()
        }
      } catch (error) {
        console.warn('Could not refresh travel plan session.', error)
      }
    },
    async scoreCurrentPlan() {
      if (!this.plan || this.scoreLoading) return
      this.scoreLoading = true
      this.scoreError = ''
      this.scoreResult = null
      try {
        const { data } = await api.post('/rpg/evals/score-current-plan', {
          input: this.planMessage,
          chatId: this.planChatId,
          plan: this.plan,
          observedToolCalls: []
        })
        this.scoreResult = data
        this.savePlanSession()
      } catch (error) {
        this.scoreError = error?.response?.data?.message || error?.message || 'Could not score this TravelPlan.'
        this.savePlanSession()
      } finally {
        this.scoreLoading = false
      }
    },
    handleChatCompleted(payload) {
      const aiContent = payload?.aiContent || ''
      const draft = this.extractPlanDraft(aiContent) || this.extractConfirmedDraft(aiContent)
      if (!draft) return
      this.chatDraft = draft
      this.chatDraftLanguage = this.preferredLanguage(draft)
      this.planMessage = draft
      this.chatDraftNotice = this.chatDraftLanguage === 'zh'
        ? '已从聊天整理出结构化计划草案，可直接生成 TravelPlan。'
        : 'Chat details ready for TravelPlan.'
      this.savePlanSession()
    },
    handleChatSubmitted() {
      this.clearChatDraftState()
      this.savePlanSession()
    },
    handleChatCleared() {
      this.clearChatCollectionState()
    },
    clearChatDraftState() {
      this.chatDraft = ''
      this.chatDraftNotice = ''
      this.chatDraftLanguage = 'en'
    },
    clearChatCollectionState() {
      this.clearChatDraftState()
      this.planMessage = DEFAULT_PLAN_MESSAGE
      this.savePlanSession()
    },
    extractPlanDraft(text) {
      const match = String(text || '').match(/(?:^|\n)\s*PLAN_DRAFT:\s*(.+?)\s*$/im)
      return match ? match[1].trim() : ''
    },
    extractConfirmedDraft(text) {
      const value = String(text || '')
      if (!/(信息[^。；\n]*(?:足够|够用)|可以点击\s*Generate\s*TravelPlan|GenerateTravelPlan)/i.test(value)) {
        return ''
      }
      const match = value.match(/(?:已记录|已确认)(?:本轮)?(?:字段|信息)?[:：]\s*([^\n。]+(?:。)?)/)
      if (!match) return ''
      return this.normalizeConfirmedDraft(match[1])
    },
    normalizeConfirmedDraft(text) {
      return String(text || '')
        .replace(/[。；;]\s*$/g, '')
        .replace(/出发地\s*([^\s，,、；;。]+)/g, '$1出发')
        .replace(/目的地\s*([^\s，,、；;。]+)/g, '去$1')
        .replace(/预算\s*(\d+)\s*(CNY|RMB|人民币)/gi, '预算$1 CNY')
        .replace(/主题\s*([^\s，,、；;。]+)/g, '主题$1')
        .replace(/偏好\s*([^\s，,、；;。]+)/g, '偏好$1')
        .replace(/\s+/g, ' ')
        .replace(/,/g, '，')
        .trim()
    },
    generatePlanFromChatDraft() {
      if (this.chatDraft) {
        this.planMessage = this.chatDraft
        this.savePlanSession()
      }
      this.generatePlan()
    },
    hasExplicitTravelers(text) {
      return /\b\d+\s*(people|persons|travelers|adults|kids|children|family members)\b/i.test(text)
        || /\bfor\s+(two|three|four|five|six|seven|eight|nine|ten)\b/i.test(text)
        || /(\d+|[一二两三四五六七八九十]+)\s*(人|位|个大人|个孩子|个成人|个小孩)/.test(text)
    },
    extractDays(text) {
      const value = String(text || '')
      const englishMatch = value.match(/\b(\d+)\s*[- ]?day\b/i)
      if (englishMatch) return Number(englishMatch[1])
      const chineseMatch = value.match(/(\d+|[一二两三四五六七八九十]+)\s*(天|日|晚)/)
      return chineseMatch ? chineseMatch[1] : ''
    },
    extractDeparture(text) {
      const value = String(text || '')
      const englishMatch = value.match(/\bfrom\s+([A-Za-z][A-Za-z\s-]*?)\s+to\b/i)
      if (englishMatch) return englishMatch[1].trim()
      const chineseMatch = value.match(/(?:从|由)(北京|上海|京都|东京|大阪|杭州|苏州|成都|云南|新疆)(?:去|到|至)/)
      if (chineseMatch) return chineseMatch[1]
      const suffixMatch = value.match(/(北京|上海|京都|东京|大阪|杭州|苏州|成都|云南|新疆)\s*出发/)
      return suffixMatch ? suffixMatch[1] : ''
    },
    normalizeMissingFieldRisk(risk) {
      const text = String(risk || '')
      if (/Still missing key information/i.test(text)) {
        return this.hasExplicitTravelers(this.planMessage) ? '' : this.travelerMissingRisk()
      }
      if (!text.includes('仍需补充关键信息')) return text
      const missing = []
      if (!this.extractDays(this.planMessage)) missing.push('天数')
      if (!this.hasExplicitTravelers(this.planMessage)) missing.push('人数')
      if (!this.hasExplicitBudget(this.planMessage)) missing.push('预算')
      if (!missing.length) return ''
      if (missing.length === 1 && missing[0] === '人数') return this.travelerMissingRisk()
      return `仍需补充：${missing.join('、')}。当前行程和预算仅作草案，确认信息后应重新校准。`
    },
    stripInventedTravelerAssumption(text) {
      return String(text || '')
        .replace(/预算按\s*\d+\s*人估算[，,。.]?/g, this.travelerMissingRisk())
        .replace(/按\s*\d+\s*人估算[，,。.]?/g, '')
        .replace(/estimated for\s*\d+\s*(people|travelers|persons)[,.]?/gi, this.travelerMissingRisk())
        .trim()
    },
    travelerMissingRisk() {
      const budget = this.plan?.budget
      const total = budget?.total && budget?.currency ? `${budget.total} ${budget.currency}` : '当前'
      return `出行人数未指定，${total} 预算需要按实际人数重新校准，尤其是机票、住宿和餐饮。`
    },
    hasExplicitBudget(text) {
      return /\b\d+(?:,\d{3})*\s*(cny|rmb|usd|jpy)\b/i.test(text)
        || /预算|(\d+|[一二两三四五六七八九十]+)\s*(万|元|块)/.test(text)
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
    },
    travelLocalResponder(text) {
      const language = this.preferredLanguage(text)
      if (this.isCapabilityQuestion(text)) {
        return {
          intent: 'capability',
          content: language === 'zh'
            ? '我是 Wayfinder Travel Agent，一个旅行规划 Demo Agent。我的作用是把模糊旅行需求整理成可展示的 TravelPlan，包括每日行程、交通、住宿假设、预算拆分和风险提示。'
            : 'I am the Wayfinder Travel Agent, a travel-planning demo agent. I turn rough trip ideas into a structured TravelPlan with daily itinerary, transport, lodging assumptions, budget breakdown, and risk notes.'
        }
      }
      if (this.isGreetingOnly(text)) {
        return {
          intent: 'greeting',
          content: language === 'zh'
            ? '你好，我是 Wayfinder Travel Agent，可以帮你把旅行想法整理成行程、交通与住宿假设、预算拆分、家庭友好提示和风险提醒。你可以告诉我目的地、出发地、天数、人数和预算。'
            : 'Hi, I am the Wayfinder Travel Agent. Tell me your destination, departure city, days, travelers, budget, and travel style, and I can draft an itinerary, transport and lodging assumptions, budget breakdown, family-friendly notes, and risks.'
        }
      }
      return null
    },
    preferredLanguage(text) {
      return /[\u4e00-\u9fff]/.test(String(text || '')) ? 'zh' : 'en'
    },
    isGreetingOnly(text) {
      const normalized = String(text || '').trim().toLowerCase().replace(/[!?.。！？\s]/g, '')
      return ['hi', 'hello', 'hey', '你好', '你好呀', '您好', '哈喽', '嗨', 'hello你好'].includes(normalized)
    },
    isCapabilityQuestion(text) {
      const value = String(text || '').trim().toLowerCase()
      return /你是谁|你是[？?]?$|你好[？?]?你是|你是干嘛|有什么用|能做什么|你能做什么/.test(value)
        || /who are you|what can you do|what are you for/.test(value)
    }
  }
}
</script>
