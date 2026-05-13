<template>
  <PageShell
    eyebrow="Tool Workshop"
    title="SyManus Tool Agent"
    subtitle="Engineering demos for fixed quality gates, bounded tool calls, generated artifacts, and traceable execution."
  >
    <section class="tool-demo-layout">
      <div class="tool-chat-column">
        <div class="tool-demo-intro">
          <p>
            SyManus is the bounded ReAct tool agent behind Wayfinder Guild. Stable demos run fixed local tasks; live tasks keep the real tool loop and may depend on model quota, API keys, or network access.
          </p>
        </div>

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
                @click="startExample(example)"
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
                @click="fillLiveTask(example)"
              >
                <strong>{{ example.label }}</strong>
                <small>{{ liveExampleDescription(example) }}</small>
              </button>
            </div>
          </section>
        </div>

        <ChatWindow
          ref="toolChat"
          :title="toolChatTitle"
          sse-path="/travel/manus/chat"
          storage-key="wayfinder.tool.chat"
          :placeholder="toolChatPlaceholder"
          :empty-text="toolChatEmptyText"
          clear-label="New Chat"
          send-label="Send"
          streaming-label="Running"
          :local-responder="toolLocalResponder"
          @submitted="handleSubmitted"
          @stream-start="handleStreamStart"
          @streaming="handleStreaming"
          @artifact="handleArtifact"
          @completed="handleCompleted"
          @failed="handleFailed"
          @greeting="handleGreeting"
          @local-response="handleLocalResponse"
          @cleared="clearSession"
        />
      </div>

      <aside class="tool-status-panel">
        <div class="status-card">
          <p class="area-kicker">Demo Status</p>
          <h2>{{ currentTask || 'No active task' }}</h2>
          <dl class="task-state-grid">
            <div>
              <dt>Mode</dt>
              <dd>{{ taskMode }}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd><span :class="['status-pill', statusClass]">{{ taskStatus }}</span></dd>
            </div>
          </dl>
        </div>

        <div class="capability-panel artifact-panel">
          <p class="area-kicker">Latest Files</p>
          <div v-if="recentArtifacts.length" class="artifact-side-list">
            <div
              v-for="artifact in recentArtifacts"
              :key="artifact.artifactId"
              class="artifact-side-card"
            >
              <strong>{{ artifact.fileName }}</strong>
              <span>{{ artifact.mimeType }}</span>
              <span>{{ formatBytes(artifact.size) }}</span>
              <span>Expires {{ formatDate(artifact.expiresAt) }}</span>
            </div>
          </div>
          <p v-else class="boundary-note">
            Portfolio Brief Pack, Trace Card Image, and successful live file tasks appear here with expiring preview/download links.
          </p>
        </div>

        <div class="trace-panel">
          <div class="timeline-head">
            <strong>Execution Trace</strong>
            <span>{{ taskMode }}</span>
          </div>
          <div class="timeline tool-trace">
            <article
              v-for="step in executionSteps"
              :key="step.id"
              :class="['timeline-item', step.state]"
            >
              <span class="timeline-dot"></span>
              <div>
                <div class="timeline-head">
                  <strong>{{ step.title }}</strong>
                  <span>{{ step.status }}</span>
                </div>
                <p>{{ step.description }}</p>
              </div>
            </article>
          </div>
        </div>
      </aside>
    </section>
  </PageShell>
</template>

<script>
import axios from '../api'
import ChatWindow from '../components/ChatWindow.vue'
import PageShell from '../components/common/PageShell.vue'

const STORAGE_KEY = 'wayfinder.tool.session'

const MODE_IDLE = 'Ready'
const MODE_PUBLIC = 'Stable Demo'
const MODE_LIVE = 'Live Tool Task'
const MODE_EXAMPLE = 'Example Prompt'
const DEFAULT_DEMO_STATUS = {
  demoMode: true,
  liveManusAvailable: false,
  searchAvailable: false,
  imageSearchAvailable: false
}

const IDLE_STEPS = [
  {
    id: 'task',
    title: 'Task framed',
    status: 'Ready',
    state: 'started',
    description: 'Choose a Stable Engineering Demo or send one bounded live tool task.'
  },
  {
    id: 'tool',
    title: 'Tool selected',
    status: 'Queued',
    state: 'skipped',
    description: 'Stable demos use fixed local runners; live tasks use SyManus tool planning.'
  },
  {
    id: 'run',
    title: 'Backend executed',
    status: 'Queued',
    state: 'skipped',
    description: 'The backend runs a fixed command, file/PDF/image generator, or live tool call.'
  },
  {
    id: 'result',
    title: 'Result delivered',
    status: 'Queued',
    state: 'skipped',
    description: 'The UI shows terminal output or expiring artifact preview and download links.'
  }
]

const LOCAL_RESPONSES = {
  greeting: 'Hi, I am the SyManus Tool Agent. Start with a Stable Engineering Demo, or send one explicit live tool task.',
  unsupportedDownload: 'This public live task area does not search for or download arbitrary resumes, CVs, private files, or unspecified external files. Use Portfolio Brief Pack for the interview artifact demo, or provide a concrete safe URL for a bounded download task.',
  resumeRedirect: 'For this showcase, generic resume requests are routed to Portfolio Brief Pack. It generates Wayfinder Guild Markdown and PDF artifacts that describe the real Travel Agent, RAG, trace, eval, guardrail, and SyManus boundaries.'
}

const ZH_RESPONSES = {
  greeting: '\u4f60\u597d\uff0c\u6211\u662f SyManus Tool Agent\u3002\u53ef\u4ee5\u5148\u8dd1 Stable Engineering Demo\uff0c\u4e5f\u53ef\u4ee5\u53d1\u9001\u4e00\u4e2a\u660e\u786e\u7684\u53d7\u9650 live tool task\u3002',
  unsupportedDownload: '\u8fd9\u4e2a public live task \u533a\u57df\u4e0d\u4f1a\u641c\u7d22\u6216\u4e0b\u8f7d\u4efb\u610f\u7b80\u5386\u3001CV\u3001\u79c1\u6709\u6587\u4ef6\u6216\u672a\u6307\u5b9a\u5916\u90e8\u6587\u4ef6\u3002\u8bf7\u4f7f\u7528 Portfolio Brief Pack\uff0c\u6216\u63d0\u4f9b\u4e00\u4e2a\u5177\u4f53\u4e14\u5b89\u5168\u7684 URL \u4f5c\u4e3a\u53d7\u9650\u4e0b\u8f7d\u4efb\u52a1\u3002',
  resumeRedirect: '\u8fd9\u4e2a\u5c55\u793a\u9875\u4f1a\u628a\u6cdb\u5316\u7b80\u5386\u8bf7\u6c42\u5f15\u5bfc\u5230 Portfolio Brief Pack\u3002\u5b83\u4f1a\u751f\u6210 Wayfinder Guild \u7684 Markdown \u548c PDF artifact\uff0c\u7528\u771f\u5b9e\u9879\u76ee\u80fd\u529b\u8bf4\u660e Travel Agent\u3001RAG\u3001Trace\u3001Eval\u3001Guardrails \u548c SyManus \u5de5\u5177\u8fb9\u754c\u3002'
}

function cloneSteps() {
  return IDLE_STEPS.map((step) => ({ ...step }))
}

export default {
  name: 'ManusChat',
  components: { ChatWindow, PageShell },
  data() {
    return {
      currentTask: '',
      taskStatus: 'Ready',
      taskMode: MODE_IDLE,
      executionSteps: cloneSteps(),
      isStreaming: false,
      recentArtifacts: [],
      demoStatus: { ...DEFAULT_DEMO_STATUS },
      stableDemoExamples: [
        {
          label: 'Wayfinder Doctor',
          description: 'skills, RPG, evals, prompts, RAG docs, naming',
          mode: MODE_PUBLIC,
          demoType: 'doctor',
          prompt: 'Run the fixed Wayfinder Doctor engineering check.'
        },
        {
          label: 'Backend Targeted Tests',
          description: 'fixed Maven quality gate',
          mode: MODE_PUBLIC,
          demoType: 'backend-tests',
          prompt: 'Run the fixed backend targeted Maven tests.'
        },
        {
          label: 'Java Runtime Check',
          description: 'allowlisted java -version',
          mode: MODE_PUBLIC,
          demoType: 'java-runtime',
          prompt: 'Run the fixed Java runtime version check.'
        },
        {
          label: 'Portfolio Brief Pack',
          description: 'Wayfinder MD + PDF artifacts',
          mode: MODE_PUBLIC,
          demoType: 'portfolio-brief-pack',
          prompt: 'Generate the Wayfinder Guild Portfolio Brief Pack.'
        },
        {
          label: 'Trace Card Image',
          description: 'fixed Wayfinder trace PNG',
          mode: MODE_PUBLIC,
          demoType: 'trace-card-image',
          prompt: 'Generate the fixed Wayfinder trace card image.'
        }
      ],
      liveTaskExamples: [
        {
          label: 'Echo Health',
          description: 'example prompt; requires live model',
          prompt: 'Run this backend tool task now: echo SyManus live health check'
        },
        {
          label: 'Write Wayfinder Note',
          description: 'example prompt; requires live model',
          prompt: 'Run this backend tool task now: write demo-note.txt with a short Wayfinder note'
        },
        {
          label: 'Kyoto Image Search',
          description: 'requires Pexels key + network',
          prompt: 'Search for one image of Kyoto station and download it as a safe artifact'
        }
      ]
    }
  },
  computed: {
    isPublicDemoMode() {
      return this.demoStatus.demoMode
    },
    liveTaskHeading() {
      return this.isPublicDemoMode ? 'Live Tool Task Examples' : 'Live Tool Tasks'
    },
    liveTaskSubheading() {
      return this.isPublicDemoMode ? 'Disabled in public demo mode' : 'Real SyManus tool loop'
    },
    liveTaskCopy() {
      if (this.isPublicDemoMode) {
        return 'These buttons only fill example prompts in public demo mode. Sending them returns the boundary message; real execution requires Owner Live Mode with model/API keys.'
      }
      const searchNote = this.demoStatus.searchAvailable ? 'live search is configured' : 'live search needs Tavily configuration'
      const imageNote = this.demoStatus.imageSearchAvailable ? 'image search is configured' : 'image search needs a Pexels key'
      return `These prompts use the real SyManus loop. Model/API access is enabled; ${searchNote}, and ${imageNote}.`
    },
    toolChatTitle() {
      return this.isPublicDemoMode ? 'Tool Task Example' : 'Live Tool Task'
    },
    toolChatPlaceholder() {
      if (this.isPublicDemoMode) {
        return 'Fill or enter an example prompt; public demo mode will return the boundary message.'
      }
      return 'Enter one bounded live task, for example: Run this backend tool task now: echo SyManus live health check'
    },
    toolChatEmptyText() {
      return this.isPublicDemoMode
        ? 'Run a Stable Engineering Demo, or fill an example prompt to see the live boundary.'
        : 'Run a Stable Engineering Demo, or send one explicit live tool task.'
    },
    statusClass() {
      return this.taskStatus.toLowerCase().replace(/\s+/g, '-')
    }
  },
  mounted() {
    this.restoreSession()
    this.fetchDemoStatus()
  },
  methods: {
    async fetchDemoStatus() {
      try {
        const { data } = await axios.get('/travel/demo-status')
        this.demoStatus = { ...DEFAULT_DEMO_STATUS, ...data }
      } catch (error) {
        this.demoStatus = { ...DEFAULT_DEMO_STATUS }
      }
    },
    startExample(example) {
      this.taskMode = example.mode || MODE_PUBLIC
      this.runStableDemo(example)
    },
    fillLiveTask(example) {
      this.currentTask = example.label
      this.taskMode = this.isPublicDemoMode ? MODE_EXAMPLE : MODE_LIVE
      this.taskStatus = 'Ready'
      this.$refs.toolChat?.fillInput(example.prompt)
      this.saveSession()
    },
    liveExampleDescription(example) {
      if (this.isPublicDemoMode) return `Fill example prompt: ${example.description}`
      return example.description
    },
    async runStableDemo(example) {
      if (this.isStreaming) return
      this.currentTask = example.label
      this.taskMode = MODE_PUBLIC
      this.taskStatus = 'Running'
      this.isStreaming = true
      this.recentArtifacts = []
      this.executionSteps = cloneSteps()
      this.markStep('task', 'Completed', 'completed')
      this.markStep('tool', 'Selected', 'completed')
      this.markStep('run', 'Running', 'started')
      this.saveSession()

      try {
        const { data } = await axios.post('/travel/manus/demo-tool', { type: example.demoType }, { timeout: 180000 })
        const rendered = this.renderDemoResult(data)
        this.$refs.toolChat?.appendLocalExchange(example.prompt, rendered.text, rendered.html)
        this.artifactsFromResult(data).forEach((artifact) => this.addArtifact(artifact))
        this.taskStatus = data?.status === 'error' ? 'Failed' : 'Completed'
        this.markStep('run', this.taskStatus === 'Failed' ? 'Failed' : 'Completed', this.taskStatus === 'Failed' ? 'failed' : 'completed')
        this.markStep('result', this.artifactsFromResult(data).length ? 'Files Ready' : this.taskStatus, this.taskStatus === 'Failed' ? 'failed' : 'completed')
      } catch (error) {
        const message = error?.response?.data?.message || error?.message || 'The backend demo endpoint is unavailable.'
        this.$refs.toolChat?.appendLocalExchange(example.prompt, `Demo failed: ${message}`)
        this.taskStatus = 'Failed'
        this.markStep('run', 'Failed', 'failed')
        this.markStep('result', 'Failed', 'failed')
      } finally {
        this.isStreaming = false
        this.saveSession()
      }
    },
    renderDemoResult(result) {
      if (this.isTerminalDemo(result?.type)) {
        return this.renderTerminalResult(result)
      }

      const artifacts = this.artifactsFromResult(result)
      if (!artifacts.length) {
        return { text: result?.message || 'Demo completed.', html: '' }
      }

      const escapedMessage = this.escapeHtml(result.message || `${artifacts.length} generated files are ready.`)
      const cards = artifacts.map((artifact) => {
        const previewUrl = this.escapeHtml(this.absoluteUrl(artifact.previewUrl))
        const downloadUrl = this.escapeHtml(this.absoluteUrl(artifact.downloadUrl))
        const fileName = this.escapeHtml(artifact.fileName)
        const mimeType = this.escapeHtml(artifact.mimeType || '')
        const expires = this.escapeHtml(this.formatDate(artifact.expiresAt))
        return `
        <div class="artifact-result">
          <strong>${this.artifactTitle(artifact)}</strong>
          ${artifact.mimeType?.startsWith('image/') ? `<img src="${previewUrl}" alt="${fileName}" />` : ''}
          <div class="artifact-meta">
            <span>${fileName}</span>
            <span>${mimeType}</span>
            <span>${this.formatBytes(artifact.size)}</span>
            <span>Expires ${expires}</span>
          </div>
          <div class="artifact-actions">
            <a href="${previewUrl}" target="_blank" rel="noreferrer">Preview</a>
            <a href="${downloadUrl}" download="${fileName}" target="_blank" rel="noreferrer">Download</a>
          </div>
        </div>
        `
      }).join('')
      const html = `<p>${escapedMessage}</p>${cards}`
      return {
        text: `${artifacts.map((artifact) => artifact.fileName).join(', ')} generated by backend tool. Preview/download links are available.`,
        html
      }
    },
    renderTerminalResult(result) {
      const title = this.terminalTitle(result?.type)
      const message = result?.message || 'Command completed.'
      const summaryItems = this.summaryItemsFromResult(result)
      const statusClass = result?.status === 'error' ? 'error' : 'success'
      const cards = summaryItems.map((item) => {
        const state = this.summaryState(item.state)
        return `
          <article class="terminal-summary-card ${state}">
            <span>${this.escapeHtml(item.label || 'Summary')}</span>
            <strong>${this.escapeHtml(item.value || '')}</strong>
            ${item.detail ? `<small>${this.escapeHtml(item.detail)}</small>` : ''}
          </article>
        `
      }).join('')
      const rawOutput = this.escapeHtml(result?.terminalOutput || '')
      const raw = rawOutput
        ? `<details class="raw-output-panel"><summary>Raw output</summary><pre>${rawOutput}</pre></details>`
        : ''
      return {
        text: `${title}: ${message}\n${summaryItems.map((item) => `${item.label}: ${item.value}`).join('\n')}`,
        html: `
          <div class="terminal-demo-result">
            <div class="terminal-demo-head">
              <span class="terminal-status ${statusClass}">${this.escapeHtml(result?.status || 'success')}</span>
              <div>
                <strong>${this.escapeHtml(title)}</strong>
                <small>${this.escapeHtml(message)}</small>
              </div>
            </div>
            <div class="terminal-summary-grid">${cards}</div>
            ${raw}
          </div>
        `
      }
    },
    summaryItemsFromResult(result) {
      if (Array.isArray(result?.summaryItems) && result.summaryItems.length) {
        return result.summaryItems.filter(Boolean)
      }
      return [{
        label: 'Result',
        value: result?.status === 'error' ? 'Needs attention' : 'Completed',
        detail: result?.message || '',
        state: result?.status === 'error' ? 'error' : 'success'
      }]
    },
    terminalTitle(type) {
      const labels = {
        doctor: 'Wayfinder Doctor',
        terminal: 'Wayfinder Doctor',
        'backend-tests': 'Backend Targeted Tests',
        'java-runtime': 'Java Runtime Check',
        'maven-version': 'Maven Version Check'
      }
      return labels[type] || 'Engineering Command'
    },
    summaryState(value) {
      return ['success', 'warning', 'error', 'info'].includes(value) ? value : 'info'
    },
    isTerminalDemo(type) {
      return ['doctor', 'terminal', 'backend-tests', 'java-runtime', 'maven-version'].includes(type)
    },
    artifactTitle(artifact) {
      if (artifact?.mimeType?.startsWith('image/')) return 'Generated Image'
      if (artifact?.mimeType === 'application/pdf') return 'Generated PDF'
      return 'Generated Text Artifact'
    },
    artifactsFromResult(result) {
      if (Array.isArray(result?.artifacts) && result.artifacts.length) {
        return result.artifacts.filter(Boolean)
      }
      return result?.artifact ? [result.artifact] : []
    },
    absoluteUrl(path) {
      if (!path) return ''
      if (/^https?:\/\//.test(path)) return path
      const base = (axios.defaults.baseURL || '').replace(/\/+$/, '')
      const joined = `${base}${path}`.replace(/([^:]\/)\/+/g, '$1')
      return new URL(joined, window.location.origin).href
    },
    escapeHtml(value) {
      return String(value || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
    },
    formatBytes(size) {
      const value = Number(size || 0)
      if (value < 1024) return `${value} B`
      if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
      return `${(value / 1024 / 1024).toFixed(1)} MB`
    },
    formatDate(value) {
      return value ? new Date(value).toLocaleString() : 'soon'
    },
    toolLocalResponder(text) {
      const language = this.preferredLanguage(text)
      if (this.isGenericResumeRequest(text)) {
        return {
          intent: 'portfolio-brief-redirect',
          content: language === 'zh' ? ZH_RESPONSES.resumeRedirect : LOCAL_RESPONSES.resumeRedirect
        }
      }
      if (this.isUnsupportedPublicDownload(text)) {
        return {
          intent: 'unsupported-public-download',
          content: language === 'zh' ? ZH_RESPONSES.unsupportedDownload : LOCAL_RESPONSES.unsupportedDownload
        }
      }
      if (!this.isGreetingOnly(text)) return null
      return {
        intent: 'greeting',
        content: language === 'zh' ? ZH_RESPONSES.greeting : LOCAL_RESPONSES.greeting
      }
    },
    preferredLanguage(text) {
      return /[\u4e00-\u9fff]/.test(String(text || '')) ? 'zh' : 'en'
    },
    isGreetingOnly(text) {
      const normalized = String(text || '').trim().toLowerCase().replace(/[!?.\u3002\uff01\uff1f\uff0c,\s]/g, '')
      return /^(hi|hello|hey|\u4f60\u597d|\u60a8\u597d)$/.test(normalized)
    },
    isGenericResumeRequest(text) {
      const value = String(text || '').trim().toLowerCase()
      const looksLikeResume = /resume|cv|\u7b80\u5386/.test(value)
      const asksGenerate = /generate|create|make|write|\u751f\u6210|\u5199/.test(value)
      const hasConcreteLivePrefix = value.includes('run this backend tool task now')
      const mentionsPortfolio = /portfolio|brief|wayfinder/.test(value)
      return looksLikeResume && asksGenerate && !hasConcreteLivePrefix && !mentionsPortfolio
    },
    isUnsupportedPublicDownload(text) {
      const value = String(text || '').trim().toLowerCase()
      const asksDownload = /download|save|\u4e0b\u8f7d/.test(value)
      const asksGenerate = /generate|create|make|write|\u751f\u6210|\u5199/.test(value)
      const looksLikeRestrictedFile = /resume|cv|private file|external file|\u7b80\u5386|\u79c1\u6709\u6587\u4ef6|\u5916\u90e8\u6587\u4ef6/.test(value)
      const hasUrl = /https?:\/\//.test(value)
      return asksDownload && looksLikeRestrictedFile && !asksGenerate && !hasUrl
    },
    handleSubmitted({ message }) {
      this.currentTask = this.displayTask(message)
      this.taskMode = this.isPublicDemoMode ? MODE_EXAMPLE : MODE_LIVE
      this.taskStatus = 'Planning'
      this.isStreaming = true
      this.recentArtifacts = []
      this.executionSteps = cloneSteps()
      this.markStep('task', 'Started', 'started')
      this.saveSession()
    },
    handleStreamStart() {
      this.taskStatus = 'Running'
      this.markStep('tool', 'Selected', 'completed')
      this.markStep('run', 'Running', 'started')
      this.saveSession()
    },
    handleStreaming() {
      this.taskStatus = 'Streaming'
      this.markStep('task', 'Completed', 'completed')
      this.markStep('tool', 'Completed', 'completed')
      this.markStep('run', 'Completed', 'completed')
      this.markStep('result', 'Streaming', 'started')
      this.saveSession()
    },
    handleArtifact({ artifact }) {
      this.addArtifact(artifact)
      this.markStep('result', artifact ? 'File Ready' : 'Streaming', artifact ? 'completed' : 'started')
      this.saveSession()
    },
    handleCompleted() {
      this.taskStatus = 'Completed'
      this.isStreaming = false
      this.markStep('result', 'Completed', 'completed')
      this.saveSession()
    },
    handleFailed() {
      this.taskStatus = 'Failed'
      this.isStreaming = false
      this.markStep('run', 'Check backend', 'failed')
      this.markStep('result', 'Failed', 'failed')
      this.saveSession()
    },
    handleGreeting() {
      this.currentTask = ''
      this.taskMode = MODE_IDLE
      this.taskStatus = 'Ready'
      this.isStreaming = false
      this.executionSteps = cloneSteps()
      this.saveSession()
    },
    handleLocalResponse() {
      this.handleGreeting()
    },
    clearSession() {
      sessionStorage.removeItem(STORAGE_KEY)
      this.currentTask = ''
      this.taskMode = MODE_IDLE
      this.taskStatus = 'Ready'
      this.executionSteps = cloneSteps()
      this.recentArtifacts = []
      this.isStreaming = false
      this.saveSession()
    },
    markStep(id, status, state) {
      this.executionSteps = this.executionSteps.map((step) => (
        step.id === id ? { ...step, status, state } : step
      ))
    },
    displayTask(message) {
      const allExamples = [...this.stableDemoExamples, ...this.liveTaskExamples]
      const matched = allExamples.find((example) => example.prompt === message)
      return matched ? matched.label : message
    },
    restoreSession() {
      try {
        const raw = sessionStorage.getItem(STORAGE_KEY)
        if (!raw) return
        const saved = JSON.parse(raw)
        this.currentTask = saved.currentTask || ''
        this.taskMode = saved.taskMode || MODE_IDLE
        this.taskStatus = saved.taskStatus || 'Ready'
        this.executionSteps = Array.isArray(saved.executionSteps) && saved.executionSteps.length
          ? saved.executionSteps
          : cloneSteps()
        this.recentArtifacts = Array.isArray(saved.recentArtifacts)
          ? saved.recentArtifacts
          : (saved.lastArtifact ? [saved.lastArtifact] : [])
        this.isStreaming = false
      } catch (error) {
        console.warn('Could not restore tool agent session.', error)
      }
    },
    saveSession() {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
        taskMode: this.taskMode,
        taskStatus: this.taskStatus,
        currentTask: this.currentTask,
        executionSteps: this.executionSteps,
        recentArtifacts: this.recentArtifacts
      }))
    },
    addArtifact(artifact) {
      if (!artifact?.artifactId) return
      const next = [
        artifact,
        ...this.recentArtifacts.filter((item) => item.artifactId !== artifact.artifactId)
      ]
      this.recentArtifacts = next.slice(0, 5)
    }
  }
}
</script>
