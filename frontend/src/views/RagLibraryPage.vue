<template>
  <PageShell
    eyebrow="Memory Library"
    title="Explainable RAG Library"
    subtitle="Ask the travel knowledge base and inspect query rewrite, retrieved documents, and final answer."
  >
    <form class="prompt-form" @submit.prevent="askRag">
      <textarea v-model="message" rows="4" placeholder="Ask a travel knowledge question..."></textarea>
      <button type="submit" :disabled="loading || !message.trim()">{{ loading ? 'Explaining...' : 'Explain RAG Answer' }}</button>
    </form>

    <StateBlock v-if="error" type="error" title="RAG request failed" :message="error" />
    <StateBlock
      v-if="response?.degraded"
      type="empty"
      title="Graceful fallback"
      :message="response.degradationReason || 'RAG explain returned a degraded response.'"
    />

    <div v-if="response" class="rag-explain-grid">
      <StarCard :title="modeTitle(response.mode)" :description="modeDescription(response.mode)">
        <template #meta>Current retrieval mode</template>
        <TagList :items="[response.degraded ? 'degraded gracefully' : 'healthy path']" />
      </StarCard>

      <StarCard title="Query Path" description="The query the user typed and the rewritten retrieval query.">
        <dl class="profile-facts">
          <div>
            <dt>Original</dt>
            <dd>{{ response.originalQuery }}</dd>
          </div>
          <div>
            <dt>Rewritten</dt>
            <dd>{{ response.rewrittenQuery }}</dd>
          </div>
          <div>
            <dt>Chat ID</dt>
            <dd>{{ response.chatId }}</dd>
          </div>
          <div>
            <dt>Documents</dt>
            <dd>{{ response.documents?.length || 0 }}</dd>
          </div>
        </dl>
      </StarCard>

      <StarCard title="Final Answer" :description="response.answer">
        <template #meta>Grounded answer</template>
      </StarCard>
    </div>

    <div v-if="response?.documents?.length" class="card-grid">
      <StarCard
        v-for="(doc, index) in response.documents"
        :key="`${doc.source}-${index}`"
        :title="doc.title || `Document ${index + 1}`"
        :description="doc.snippet"
      >
        <template #meta>{{ doc.source || 'unknown source' }}</template>
        <TagList :items="[scoreLabel(doc.score)]" />
      </StarCard>
    </div>
  </PageShell>
</template>

<script>
import api from '../api'
import PageShell from '../components/common/PageShell.vue'
import StarCard from '../components/common/StarCard.vue'
import TagList from '../components/common/TagList.vue'
import StateBlock from '../components/common/StateBlock.vue'

export default {
  name: 'RagLibraryPage',
  components: { PageShell, StarCard, TagList, StateBlock },
  data() {
    return {
      message: 'What should I consider for a relaxed family trip to Japan?',
      response: null,
      loading: false,
      error: ''
    }
  },
  methods: {
    async askRag() {
      this.loading = true
      this.error = ''
      this.response = null
      try {
        const { data } = await api.post('/travel/rag/explain', {
          message: this.message,
          chatId: `rag-explain-${Date.now()}`
        })
        this.response = data
      } catch (err) {
        this.error = 'Could not query the explainable RAG endpoint. The legacy RAG endpoint remains unchanged.'
      } finally {
        this.loading = false
      }
    },
    scoreLabel(score) {
      return score === null || score === undefined ? 'score: n/a' : `score: ${Number(score).toFixed(3)}`
    },
    modeTitle(mode) {
      const normalized = mode || 'demo'
      if (normalized === 'pgvector') return 'PgVector Live'
      if (normalized === 'lightweight' || normalized === 'lightweight-fallback') return 'Lightweight Retrieval'
      return 'Demo Mode'
    },
    modeDescription(mode) {
      const normalized = mode || 'demo'
      if (normalized === 'pgvector') {
        return 'Owner Live Mode uses VectorStore retrieval for deeper local or controlled demos.'
      }
      if (normalized === 'lightweight-fallback') {
        return 'PgVector was requested but unavailable, so the public-safe Markdown retriever handled the request without a server error.'
      }
      if (normalized === 'lightweight') {
        return 'Public cost-control mode searches local Markdown snippets without PgVector or cloud database cost.'
      }
      return 'Public demo mode returns a stable RAG explanation without PgVector or database cost.'
    }
  }
}
</script>
