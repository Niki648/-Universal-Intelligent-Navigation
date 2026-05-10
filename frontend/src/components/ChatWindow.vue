<template>
  <div class="chat-root">
    <div class="chat-header">
      <span>{{ title }}</span>
      <small>{{ chatId }}</small>
    </div>
    <div ref="body" class="chat-body">
      <div
        v-for="(message, index) in messages"
        :key="index"
        :class="['msg', message.role]"
      >
        <div class="bubble" v-html="formatMessage(message.content)"></div>
      </div>
      <div v-if="messages.length === 0" class="empty-chat">
        Start with a concrete question and watch how the Agent responds.
      </div>
    </div>
    <form class="chat-footer" @submit.prevent="send">
      <input
        v-model="input"
        :disabled="streaming"
        :placeholder="placeholder"
      />
      <button type="submit" :disabled="streaming || !input.trim()">
        {{ streaming ? 'Streaming' : 'Send' }}
      </button>
    </form>
  </div>
</template>

<script>
import axios from '../api'

export default {
  props: {
    title: { type: String, default: 'Chat' },
    ssePath: { type: String, required: true },
    placeholder: { type: String, default: 'Type a message, then press Enter' }
  },
  data() {
    return {
      chatId: '',
      input: '',
      messages: [],
      es: null,
      currentAiIndex: -1,
      streaming: false
    }
  },
  mounted() {
    this.chatId = this.generateChatId()
  },
  beforeUnmount() {
    this.closeStream()
  },
  methods: {
    generateChatId() {
      return `chat-${Math.random().toString(36).slice(2, 10)}`
    },
    send() {
      const text = this.input.trim()
      if (!text || this.streaming) return

      this.input = ''
      this.messages.push({ role: 'user', content: text })
      this.currentAiIndex = this.messages.push({ role: 'ai', content: '' }) - 1
      this.streaming = true
      this.scrollToBottom()

      const base = (axios.defaults.baseURL || '').replace(/\/+$/, '')
      const params = new URLSearchParams({ message: text, chatId: this.chatId })
      const url = `${base}${this.ssePath}?${params.toString()}`

      this.closeStream()
      this.es = new EventSource(url)
      this.es.onmessage = (event) => {
        if (!event.data || event.data === '[DONE]' || event.data === '__DONE__') {
          this.finishStream()
          return
        }
        this.messages[this.currentAiIndex].content += event.data
        this.scrollToBottom()
      }
      this.es.onerror = () => {
        if (!this.messages[this.currentAiIndex]?.content) {
          this.messages[this.currentAiIndex].content = '[Hint] The stream ended or the backend is unavailable. Please check that the backend service is running.'
        }
        this.finishStream()
      }
    },
    finishStream() {
      this.streaming = false
      this.currentAiIndex = -1
      this.closeStream()
      this.scrollToBottom()
    },
    closeStream() {
      if (this.es) {
        this.es.close()
        this.es = null
      }
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.body
        if (el) el.scrollTop = el.scrollHeight
      })
    },
    formatMessage(text) {
      if (!text) return ''
      return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/\r\n/g, '\n')
        .replace(/\n/g, '<br/>')
    }
  }
}
</script>
