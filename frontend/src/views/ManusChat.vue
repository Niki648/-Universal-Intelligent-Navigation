<template>
  <section class="chat-page">
    <router-link class="back-link" to="/">&lt;- Back to Wayfinder Guild</router-link>
    <p class="eyebrow">Tool Workshop / &#30334;&#24037;&#24037;&#22346;</p>
    <h1>SyManus Tool Agent</h1>
    <p class="subtitle">&#20445;&#30041;&#21407;&#26377;&#24037;&#20855; Agent &#32842;&#22825;&#20837;&#21475;&#65292;&#29992;&#26469;&#23637;&#31034;&#24037;&#20855;&#35843;&#29992;&#12289;&#19978;&#19979;&#25991;&#21644;&#27969;&#24335;&#25191;&#34892;&#20307;&#39564;&#12290;</p>
    <ChatWindow
      title="SyManus Tool Agent"
      sse-path="/travel/manus/chat"
      placeholder="Enter a task for the tool agent, then press Enter"
    />
  </section>
</template>

<script>
import ChatWindow from '../components/ChatWindow.vue'

export default {
  components: { ChatWindow }
}
</script>
