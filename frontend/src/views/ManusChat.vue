<template>
  <div>
    <h2>AI 超级智能体</h2>
    <ChatWindow :title="'AI 超级智能体'" sse-path="/travel/manus/chat" />
  </div>
</template>

<script>
import ChatWindow from '../components/ChatWindow.vue'
export default { components: { ChatWindow } }
</script>
