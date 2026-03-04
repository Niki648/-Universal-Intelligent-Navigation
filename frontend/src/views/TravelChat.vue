<template>
  <div>
    <h2>寰宇智导应用</h2>
    <ChatWindow :title="'寰宇智导'" sse-path="/travel/chat/stream" />
  </div>
</template>

<script>
import ChatWindow from '../components/ChatWindow.vue'
export default { components: { ChatWindow } }
</script>
