<template>
  <PageShell
    eyebrow="Tool Workshop"
    title="SyManus Tool Agent"
    subtitle="Backend tool orchestration: fixed tasks, tool calls, generated files, and execution trace."
  >
    <section class="tool-demo-layout">
      <div class="tool-chat-column">
        <div class="tool-demo-intro">
          <p>
            SyManus shows how a bounded backend Tool Agent moves from task routing to tool execution and generated file delivery.
          </p>
        </div>

        <div class="tool-prompt-board" aria-label="SyManus demo tasks">
          <section class="prompt-group stable-demo">
            <div class="prompt-group-head">
              <strong>Stable Demo</strong>
              <span>Fixed tool tasks</span>
            </div>
            <p class="prompt-copy">
            Fixed demos cover a project quality check, text file generation, PDF generation, and image preview.
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

        </div>

        <ChatWindow
          ref="toolChat"
          title="Advanced Live Task"
          sse-path="/travel/manus/chat"
          storage-key="wayfinder.tool.chat"
          placeholder="Describe one bounded live tool task, for example: write demo-note.txt with a short note."
          empty-text="Start with a Stable Demo, or enter a clear live task for the backend tool policy."
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
            Run a Text / PDF / Image demo to see recent generated files, type, size, and expiry.
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
const MODE_WALKTHROUGH = 'Walkthrough'
const MODE_PUBLIC = 'Stable Demo'
const MODE_LIVE = 'Live Task'

const IDLE_STEPS = [
  {
    id: 'task',
    title: 'Task framed',
    status: 'Ready',
    state: 'started',
    description: 'Choose a Stable Demo or enter one clear bounded live tool task.'
  },
  {
    id: 'tool',
    title: 'Tool selected',
    status: 'Queued',
    state: 'skipped',
    description: 'SyManus selects one registered backend tool for the task.'
  },
  {
    id: 'run',
    title: 'Backend executed',
    status: 'Queued',
    state: 'skipped',
    description: 'The backend runs the tool and returns either a result or generated file metadata.'
  },
  {
    id: 'result',
    title: 'Result delivered',
    status: 'Queued',
    state: 'skipped',
    description: 'The UI shows the summary plus preview and download links when a file is generated.'
  }
]

const LOCAL_RESPONSES = {
  greeting: 'Hi, I am the SyManus Tool Agent. Start with a Stable Demo, or ask for one clear bounded live tool task.',
  unsupportedDownload: 'This public Tool Agent demo does not search for or download arbitrary resumes, CVs, private files, or unspecified external files. Online downloads come from fixed expiring demo files such as demo-note.txt, demo-note.pdf, and demo-ocean.png.'
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
      stableDemoExamples: [
        {
          label: 'Wayfinder Doctor',
          description: 'Project quality check',
          mode: MODE_PUBLIC,
          demoType: 'doctor',
          prompt: 'Run the fixed Wayfinder Doctor project quality check demo.'
        },
        {
          label: 'Java Resume Pack',
          description: 'Generate MD + PDF resume',
          mode: MODE_PUBLIC,
          demoType: 'resume-pack',
          prompt: 'Generate a Java backend resume pack as safe backend files.'
        },
        {
          label: 'Text File',
          description: 'Generate demo-note.txt',
          mode: MODE_PUBLIC,
          demoType: 'file',
          prompt: 'Generate demo-note.txt as a safe backend file.'
        },
        {
          label: 'PDF File',
          description: 'Generate demo-note.pdf',
          mode: MODE_PUBLIC,
          demoType: 'pdf',
          prompt: 'Generate demo-note.pdf as a safe backend file.'
        },
        {
          label: 'Image Preview',
          description: 'Preview demo-ocean.png',
          mode: MODE_PUBLIC,
          demoType: 'image',
          prompt: 'Generate the fixed ocean image as a safe backend file.'
        }
      ]
    }
  },
  computed: {
    statusClass() {
      return this.taskStatus.toLowerCase().replace(/\s+/g, '-')
    }
  },
  mounted() {
    this.restoreSession()
  },
  methods: {
    startExample(example) {
      this.taskMode = example.mode || MODE_PUBLIC
      this.runStableDemo(example)
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
        const { data } = await axios.post('/travel/manus/demo-tool', { type: example.demoType })
        const rendered = this.renderDemoResult(data)
        this.$refs.toolChat?.appendLocalExchange(example.prompt, rendered.text, rendered.html)
        this.artifactsFromResult(data).forEach((artifact) => this.addArtifact(artifact))
        this.taskStatus = 'Completed'
        this.markStep('run', 'Completed', 'completed')
        this.markStep('result', this.artifactsFromResult(data).length ? 'Files Ready' : 'Completed', 'completed')
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
      if (result?.type === 'doctor' || result?.type === 'terminal') {
        const output = this.escapeHtml(result.terminalOutput || '')
        const message = this.escapeHtml(result.message || 'Wayfinder Doctor completed.')
        return {
          text: `${result.message || 'Wayfinder Doctor completed.'}\nOutput:\n${result.terminalOutput || ''}`,
          html: `<p>${message}</p><pre>${output}</pre>`
        }
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
        const expires = this.formatDate(artifact.expiresAt)
        return `
        <div class="artifact-result">
          <strong>Generated File</strong>
          ${artifact.mimeType?.startsWith('image/') ? `<img src="${previewUrl}" alt="${fileName}" />` : ''}
          <div class="artifact-meta">
            <span>${fileName}</span>
            <span>${mimeType}</span>
            <span>${this.formatBytes(artifact.size)}</span>
            <span>Expires ${this.escapeHtml(expires)}</span>
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
      if (this.isUnsupportedPublicDownload(text)) {
        return {
          intent: 'unsupported-public-download',
          content: language === 'zh'
            ? LOCAL_RESPONSES.unsupportedDownload
            : 'This public Tool Agent demo does not search for or download arbitrary resumes, CVs, private files, or unspecified external files. Online downloads come from fixed expiring demo artifacts such as demo-note.txt, demo-note.pdf, and demo-ocean.png.'
        }
      }
      if (!this.isGreetingOnly(text)) return null
      return {
        intent: 'greeting',
        content: language === 'zh'
          ? LOCAL_RESPONSES.greeting
          : 'Hi, I am the SyManus Tool Agent. Start with a Stable Demo, or ask for one clear bounded live tool task.'
      }
    },
    preferredLanguage(text) {
      return /[\u4e00-\u9fff]/.test(String(text || '')) ? 'zh' : 'en'
    },
    isGreetingOnly(text) {
      const normalized = String(text || '').trim().toLowerCase().replace(/[!?.。！？，,\s]/g, '')
      return ['hi', 'hello', 'hey', '你好', '您好', '哈喽'].includes(normalized)
    },
    isUnsupportedPublicDownload(text) {
      const value = String(text || '').trim().toLowerCase()
      const asksDownload = /download|save|下载/.test(value)
      const asksGenerate = /generate|create|make|write|生成|写/.test(value)
      const looksLikeResume = /resume|cv|backend|java|后端|简历|private file|external file|私有文件|外部文件/.test(value)
      const hasUrl = /https?:\/\//.test(value)
      return asksDownload && looksLikeResume && !asksGenerate && !hasUrl
    },
    handleSubmitted({ message }) {
      this.currentTask = this.displayTask(message)
      this.taskMode = MODE_LIVE
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
      const live = this.stableDemoExamples.find((example) => example.prompt === message)
      return live ? live.label : message
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
