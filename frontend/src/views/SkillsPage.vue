<template>
  <PageShell
    eyebrow="Constellation Hall"
    title="Skills System"
    subtitle="Reusable engineering knowledge arranged as cards the Agent can follow."
  >
    <StarCard title="Skill Matching Demo" description="Type a travel request and see which backend Skills would be loaded before planning.">
      <form class="prompt-form" @submit.prevent="matchSkills">
        <textarea v-model="matchMessage" rows="3" placeholder="Example: I will travel to Japan with my parents for 7 days, budget 20000 CNY, relaxed pace."></textarea>
        <button type="submit" :disabled="matching || !matchMessage.trim()">{{ matching ? 'Matching...' : 'Match Skills' }}</button>
      </form>
      <StateBlock v-if="matchError" type="error" title="Match failed" :message="matchError" />
      <div v-if="matchedSkills.length" class="match-results">
        <article v-for="match in matchedSkills" :key="match.id" class="match-result">
          <strong>{{ match.name }}</strong>
          <span>{{ match.matchedReason }}</span>
          <TagList :items="match.triggers || []" />
        </article>
      </div>
    </StarCard>

    <StateBlock v-if="loading" type="loading" title="Loading skills" message="Fetching /api/rpg/skills." />
    <StateBlock v-else-if="error" type="error" title="Skills API unavailable" message="Showing local fallback skill cards." />

    <div class="card-grid">
      <StarCard
        v-for="skill in normalizedSkills"
        :key="skill.id"
        :class="{ highlighted: isMatched(skill) }"
        :title="skill.name"
        :description="skill.description"
      >
        <template #meta>
          <span>{{ skill.rpgName }}</span>
          <span class="rarity">Level {{ skill.level }}</span>
          <span v-if="isMatched(skill)" class="match-badge">Matched</span>
        </template>
        <TagList :items="[skill.category, ...skill.keywords]" />
        <div class="section-block">
          <h3>Use Cases</h3>
          <ul class="feature-list">
            <li v-for="item in skill.useCases" :key="item">{{ item }}</li>
          </ul>
        </div>
        <div class="section-block">
          <h3>Related Agents</h3>
          <TagList :items="skill.relatedAgents" />
        </div>
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

const SKILL_FALLBACKS = {
  'java-backend-architecture': { rpgName: 'Backend Compass', relatedAgents: ['Wayfinder Guild', 'Travel Agent'] },
  'spring-ai-agent-design': { rpgName: 'Agent Lantern', relatedAgents: ['Travel Agent', 'SyManus'] },
  'rag-knowledge-engineering': { rpgName: 'Memory Star', relatedAgents: ['RAG Library'] },
  'agent-evaluation': { rpgName: 'Lighthouse Lens', relatedAgents: ['Eval Harness'] },
  'guardrail-design': { rpgName: 'Safety Rail', relatedAgents: ['Travel Agent', 'Tool Agent'] }
}

const FALLBACK_SKILLS = [
  {
    id: 'spring-ai-agent-design',
    name: 'Spring AI Agent Design',
    category: 'Agent',
    level: 'advanced',
    description: 'Designs chat, structured output, tool calling, and traceable Agent flows.',
    keywords: ['Spring AI', 'DeepSeek', 'ChatClient']
  }
]

export default {
  name: 'SkillsPage',
  components: { PageShell, StarCard, TagList, StateBlock },
  data() {
    return {
      skills: FALLBACK_SKILLS,
      loading: true,
      error: false,
      matchMessage: 'I will travel to Japan with my parents for 7 days, budget 20000 CNY, relaxed pace.',
      matchedSkills: [],
      matching: false,
      matchError: ''
    }
  },
  computed: {
    normalizedSkills() {
      return this.skills.map((skill) => {
        const fallback = SKILL_FALLBACKS[skill.id] || {}
        const keywords = skill.keywords || []
        return {
          id: skill.id || skill.name,
          name: skill.name,
          rpgName: skill.rpgName || fallback.rpgName || `${skill.category || 'Skill'} Star`,
          level: skill.level || 'intermediate',
          category: skill.category || 'Engineering',
          description: skill.description || '',
          keywords,
          useCases: skill.useCases || [
            `Apply ${skill.name} in a real backend capability.`,
            'Explain the engineering boundary in portfolio interviews.'
          ],
          relatedAgents: skill.relatedAgents || fallback.relatedAgents || ['Travel Agent']
        }
      })
    },
    matchedIds() {
      return new Set(this.matchedSkills.map((skill) => skill.id))
    },
    matchedCategories() {
      return new Set(this.matchedSkills.map((skill) => (skill.category || '').toLowerCase()))
    }
  },
  async mounted() {
    try {
      const { data } = await api.get('/rpg/skills')
      this.skills = Array.isArray(data) && data.length ? data : FALLBACK_SKILLS
    } catch (err) {
      this.error = true
    } finally {
      this.loading = false
    }
  },
  methods: {
    async matchSkills() {
      this.matching = true
      this.matchError = ''
      this.matchedSkills = []
      try {
        const { data } = await api.post('/rpg/skills/match', { message: this.matchMessage })
        this.matchedSkills = Array.isArray(data) ? data : []
      } catch (err) {
        this.matchError = 'Could not call /api/rpg/skills/match. Showing no matches.'
      } finally {
        this.matching = false
      }
    },
    isMatched(skill) {
      return this.matchedIds.has(skill.id) || this.matchedCategories.has((skill.category || '').toLowerCase())
    }
  }
}
</script>
