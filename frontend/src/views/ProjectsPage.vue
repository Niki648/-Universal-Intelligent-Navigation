<template>
  <PageShell
    eyebrow="Voyage Forge"
    title="Project Portfolio"
    subtitle="Engineering works shaped like vessels ready for a longer voyage."
  >
    <StateBlock v-if="loading" type="loading" title="Loading projects" message="Fetching /api/rpg/projects." />
    <StateBlock v-else-if="error" type="error" title="Projects API unavailable" message="Showing local fallback project cards." />

    <div class="card-grid">
      <StarCard
        v-for="project in normalizedProjects"
        :key="project.id"
        :title="project.name"
        :description="project.description"
      >
        <template #meta>
          <span>{{ project.rpgName }}</span>
          <span class="rarity">{{ project.rarity }}</span>
        </template>
        <TagList :items="project.techStack" />
        <div class="section-block">
          <h3>Highlights</h3>
          <ul class="feature-list">
            <li v-for="highlight in project.highlights" :key="highlight">{{ highlight }}</li>
          </ul>
        </div>
        <div class="card-actions">
          <router-link v-if="project.demoRoute" :to="project.demoRoute">Open Demo</router-link>
          <a v-if="project.githubUrl" :href="project.githubUrl" target="_blank" rel="noreferrer">GitHub</a>
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

const PROJECT_FALLBACKS = {
  'wayfinder-guild': { rpgName: 'Travel Cabin Core', rarity: 'Legendary', demoRoute: '/travel-agent' },
  'symanus-tool-agent': { rpgName: 'Tool Workshop Engine', rarity: 'Rare', demoRoute: '/manus-agent' },
}

const FALLBACK_PROJECTS = [
  {
    id: 'wayfinder-guild',
    name: 'Wayfinder Guild',
    description: 'RPG portfolio frontend and backend foundation for showcasing Agent engineering.',
    tags: ['Vue', 'Phaser', 'Spring Boot', 'Portfolio'],
    highlights: ['Interactive RPG map', 'Backend metadata APIs', 'Capability pages for interview storytelling']
  }
]

export default {
  name: 'ProjectsPage',
  components: { PageShell, StarCard, TagList, StateBlock },
  data() {
    return {
      projects: FALLBACK_PROJECTS,
      loading: true,
      error: false
    }
  },
  computed: {
    normalizedProjects() {
      return this.projects.map((project) => {
        const fallback = PROJECT_FALLBACKS[project.id] || {}
        return {
          id: project.id || project.name,
          name: project.name,
          rpgName: project.rpgName || fallback.rpgName || project.subtitle || 'Voyage Work',
          rarity: project.rarity || fallback.rarity || 'Rare',
          description: project.description || project.subtitle || '',
          techStack: project.techStack || project.tags || [],
          highlights: project.highlights || [],
          githubUrl: project.githubUrl || project.links?.[0] || '',
          demoRoute: project.demoRoute || fallback.demoRoute || ''
        }
      })
    }
  },
  async mounted() {
    try {
      const { data } = await api.get('/rpg/projects')
      this.projects = Array.isArray(data) && data.length ? data : FALLBACK_PROJECTS
    } catch (err) {
      this.error = true
    } finally {
      this.loading = false
    }
  }
}
</script>
