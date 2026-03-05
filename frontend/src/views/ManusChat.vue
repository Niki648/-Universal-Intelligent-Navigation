<template>
  <div>
    <h2>AI 超级智能体</h2>
    <ChatWindow
      title="AI 超级智能体"
      sse-path="/travel/manus/chat"
      placeholder="输入消息（可含创作者名或链接），回车发送"
    />
  </div>
</template>

<script>
import ChatWindow from "../components/ChatWindow.vue";
export default { components: { ChatWindow } };
</script>
