<template>
  <PageShell
    eyebrow="Wayfinder Architecture"
    title="System Architecture"
    subtitle="A compact map of how the Agentic Travel Backend becomes a portfolio-grade AI system."
  >
    <div class="architecture-flow">
      <StarCard
        v-for="node in flow"
        :key="node.title"
        :title="node.title"
        :description="node.description"
        :meta="node.meta"
      />
    </div>

    <div class="card-grid">
      <StarCard
        v-for="module in modules"
        :key="module.title"
        :title="module.title"
        :description="module.description"
      >
        <TagList :items="module.tags" />
      </StarCard>
    </div>

    <div class="section-block">
      <h2 class="section-title">Guardrails in the Guild</h2>
      <div class="card-grid">
        <StarCard
          v-for="guardrail in guardrails"
          :key="guardrail.title"
          :title="guardrail.title"
          :description="guardrail.description"
        >
          <template #meta>{{ guardrail.layer }}</template>
          <TagList :items="guardrail.examples" />
        </StarCard>
      </div>
    </div>
  </PageShell>
</template>

<script>
import PageShell from '../components/common/PageShell.vue'
import StarCard from '../components/common/StarCard.vue'
import TagList from '../components/common/TagList.vue'

export default {
  name: 'ArchitecturePage',
  components: { PageShell, StarCard, TagList },
  data() {
    return {
      flow: [
        { title: 'WayfinderTravelController', meta: 'API Gate', description: 'Keeps HTTP protocol handling thin and forwards travel requests into application services.' },
        { title: 'WayfinderTravelFacade', meta: 'Compatibility Facade', description: 'Preserves existing API behavior while delegating real work to split services and orchestration.' },
        { title: 'TravelOrchestratorService', meta: 'Agent Workflow', description: 'Coordinates requirement collection, itinerary planning, budget estimation, risk advice, and report composition.' }
      ],
      modules: [
        { title: 'Skills', description: 'Markdown-based domain rules loaded from resources and selected for user travel requests.', tags: ['SkillLoaderService', 'classpath resources', 'domain rules'] },
        { title: 'TravelPlan', description: 'Structured output model used by the frontend to render reliable travel planning cards.', tags: ['schema output', 'budget', 'itineraryDays'] },
        { title: 'Eval Harness', description: 'Case-based quality checks for whether the Agent asks, plans, estimates, warns, and avoids unsafe promises.', tags: ['travel-cases.json', 'regression', 'quality'] },
        { title: 'Guardrails', description: 'Input and output safety checks that reduce irrelevant requests, unsafe wording, and overconfident answers.', tags: ['input inspection', 'output sanitization'] },
        { title: 'Agent Trace', description: 'Observable events that show each step from user intent to skills, planning, budget, and risk checks.', tags: ['timeline', 'SSE', 'debugging'] },
        { title: 'RAG', description: 'Knowledge retrieval path for grounded travel answers from local documents.', tags: ['retrieval', 'knowledge context'] },
        { title: 'MCP', description: 'Optional external tool/server integration boundary for richer Agent capabilities.', tags: ['tool boundary', 'external capability'] }
      ],
      guardrails: [
        {
          title: 'Input Guardrail',
          layer: 'Before planning',
          description: 'Screens user intent and blocks or redirects requests that do not belong in travel planning.',
          examples: ['prompt injection blocked', 'non-travel request softened']
        },
        {
          title: 'Tool Guardrail',
          layer: 'Before execution',
          description: 'Keeps tools inside intended boundaries before file, terminal, or resource operations run.',
          examples: ['path traversal blocked', 'dangerous terminal command blocked']
        },
        {
          title: 'Output Guardrail',
          layer: 'After generation',
          description: 'Softens risky claims and keeps uncertainty visible in user-facing answers.',
          examples: ['visa guarantee softened', 'absolute safety claim softened']
        }
      ]
    }
  }
}
</script>
