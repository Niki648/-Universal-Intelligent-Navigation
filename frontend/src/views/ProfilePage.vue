<template>
  <PageShell
    eyebrow="Profile Journal"
    :title="profile.name || 'SeeWhy'"
    :subtitle="profile.title || 'Java & AI Application Engineer'"
  >
    <StateBlock
      v-if="loading"
      type="loading"
      title="Loading profile"
      message="Reading the travel journal from /api/rpg/profile."
    />
    <StateBlock
      v-else-if="error"
      type="error"
      title="Profile API unavailable"
      message="Showing local fallback profile data for now."
    />

    <div class="profile-layout">
      <StarCard class="profile-hero" :description="profile.summary">
        <template #meta>{{ profile.role || 'The Wayfinder' }}</template>
        <dl class="profile-facts">
          <div>
            <dt>Location</dt>
            <dd>{{ profile.location || 'China' }}</dd>
          </div>
          <div v-for="(value, key) in profile.stats || {}" :key="key">
            <dt>{{ key }}</dt>
            <dd>{{ value }}</dd>
          </div>
        </dl>
      </StarCard>

      <StarCard title="Capability Tags" description="The practical engineering areas behind the RPG role.">
        <TagList :items="profile.focusAreas || []" />
      </StarCard>

      <StarCard title="Strengths" description="What the portfolio should communicate in interviews.">
        <ul class="feature-list">
          <li v-for="item in profile.strengths || []" :key="item">{{ item }}</li>
        </ul>
      </StarCard>

      <StarCard title="Journey Notes" description="Experience and achievements will be expanded here as the portfolio matures.">
        <div class="milestone-list">
          <div v-for="item in milestones" :key="item.title" class="milestone">
            <strong>{{ item.title }}</strong>
            <span>{{ item.text }}</span>
          </div>
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

const FALLBACK_PROFILE = {
  name: 'SeeWhy',
  title: 'Java & AI Application Engineer',
  role: 'The Wayfinder',
  location: 'China',
  summary: 'A backend-oriented AI application engineer building Wayfinder Guild into a runnable Agent engineering portfolio.',
  focusAreas: ['Java Backend', 'Spring AI', 'Agent Workflows', 'RAG', 'Eval', 'Trace', 'Guardrails'],
  strengths: [
    'Turns complex AI capabilities into clear backend service boundaries.',
    'Values testability, observability, and demo-ready product experience.',
    'Connects architecture decisions with user-facing product stories.'
  ],
  stats: {
    backend: 'Spring Boot 3.4 + Java 21',
    ai: 'Spring AI + DeepSeek',
    portfolio: 'Wayfinder Guild'
  }
}

export default {
  name: 'ProfilePage',
  components: { PageShell, StarCard, TagList, StateBlock },
  data() {
    return {
      profile: FALLBACK_PROFILE,
      loading: true,
      error: false,
      milestones: [
        { title: 'Agentic Backend', text: 'Travel planning backend split into chat, plan, RAG, tool, trace, eval, and guardrail modules.' },
        { title: 'Portfolio System', text: 'RPG metadata API and Phaser/Vue frontend turn engineering capabilities into an explorable town.' },
        { title: 'Interview Narrative', text: 'Each building explains a real AI engineering capability, not just a visual theme.' }
      ]
    }
  },
  async mounted() {
    try {
      const { data } = await api.get('/rpg/profile')
      this.profile = { ...FALLBACK_PROFILE, ...data }
    } catch (err) {
      this.error = true
    } finally {
      this.loading = false
    }
  }
}
</script>
