<template>
  <div class="chat-root">
    <div class="chat-header">{{ title }} — 聊天</div>
    <div class="chat-body" ref="body">
      <div
        v-for="(m, idx) in messages"
        :key="idx"
        :class="['msg', m.role, bubbleType(m)]"
      >
        <div class="bubble" v-html="formatMessage(m.content)"></div>
      </div>
    </div>
    <div class="chat-footer">
      <input
        v-model="input"
        @keyup.enter="send"
        placeholder="输入消息，回车发送"
      />
      <button @click="send">发送</button>
    </div>
  </div>
</template>

<script>
import axios from "../api";

export default {
  props: {
    title: { type: String, default: "Chat" },
    ssePath: { type: String, required: true },
  },
  data() {
    return {
      chatId: null,
      input: "",
      messages: [],
      es: null,
      esUrl: null,
      reconnectAttempts: 0,
      maxReconnects: 3,
      currentAiIndex: -1,
      lastChunk: "",
      repeatCount: 0,
      maxRepeatCount: 5,
    };
  },
  beforeUnmount() {
    try {
      if (this.es) this.es.close();
    } catch (_) {}
  },
  mounted() {
    this.chatId = this.generateChatId();
  },
  methods: {
    generateChatId() {
      return "chat-" + Math.random().toString(36).substr(2, 8);
    },
    bubbleType(m) {
      const text = (m && m.content) || "";
      if (text.startsWith("[错误]")) return "msg-error";
      if (text.startsWith("[提示]")) return "msg-hint";
      return "";
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.body;
        if (el) el.scrollTop = el.scrollHeight;
      });
    },
    formatMessage(text) {
      if (!text) return "";
      // 先做基本转义，避免 XSS，再做很轻量的 Markdown 样式处理
      let safe = String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
      // **加粗**
      safe = safe.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
      // 分隔线 ---
      safe = safe.replace(/^---$/gm, "<hr/>");
      // 换行 -> <br>
      safe = safe.replace(/\r\n/g, "\n").replace(/\n/g, "<br/>");
      return safe;
    },
    send() {
      const text = (this.input || "").trim();
      if (!text) return;
      // append user message
      this.messages.push({ role: "user", content: text });
      this.input = "";
      this.scrollToBottom();

      // open EventSource to receive SSE from backend
      // Build a relative URL using axios.defaults.baseURL (e.g. '/api') and the provided ssePath
      const base = (axios.defaults.baseURL || "").replace(/\/+$/, "");
      const path = base + this.ssePath;
      const params = new URLSearchParams({
        message: text,
        chatId: this.chatId,
      });
      const urlStr = path + "?" + params.toString();
      console.log("Opening SSE URL:", urlStr);

      // quick HEAD-like check using fetch to detect 404 before creating EventSource
      const controller = new AbortController();
      // helper to try opening EventSource for a given path
      const tryOpen = (candidatePath) => {
        const candidateUrl = candidatePath + "?" + params.toString();
        console.log("Trying SSE candidate:", candidateUrl);
        // Use HEAD to probe the endpoint without initiating a streaming response
        return fetch(candidateUrl, {
          method: "HEAD",
          signal: controller.signal,
        })
          .then((resp) => {
            // Some servers may not accept HEAD and return 405; treat 405 as "exists"
            if (resp.ok || resp.status === 405) {
              return { ok: true, url: candidateUrl, status: resp.status };
            }
            return { ok: false, status: resp.status };
          })
          .catch((err) => {
            // Network errors or CORS may cause HEAD to fail. Surface the error so
            // the candidate fallback logic can try other endpoints.
            return { ok: false, err };
          });
      };

      const candidates = [path];
      // if initial path is the travel chat SSE, add other possible endpoints to try
      if (this.ssePath === "/travel/chat/sse") {
        candidates.push(base + "/travel/chat/sse_emitter");
        candidates.push(base + "/travel/chat/server_sent_event");
      }

      // close previous EventSource if exists to avoid multiple open connections
      try {
        if (this.es) {
          this.es.close();
          this.es = null;
        }
      } catch (_) {}

      // sequentially try candidates
      const trySequential = async (idx = 0) => {
        if (idx >= candidates.length) {
          this.messages.push({
            role: "ai",
            content: `[错误] 所有 SSE 尝试均失败`,
          });
          return;
        }
        const candidate = candidates[idx];
        try {
          const result = await tryOpen(candidate);
          if (!result.ok) {
            console.warn(
              "Candidate failed",
              candidate,
              result.status || result.err,
            );
            if (result.status === 404) {
              // try next candidate
              return trySequential(idx + 1);
            }
            this.messages.push({
              role: "ai",
              content: `[错误] SSE 请求返回 ${result.status || "网络错误"}`,
            });
            return;
          }
          controller.abort();
          try {
            // create and store EventSource instance
            this.esUrl = result.url;
            this.reconnectAttempts = 0;
            // prepare a single AI message placeholder for streaming
            if (this.currentAiIndex === -1) {
              this.currentAiIndex =
                this.messages.push({
                  role: "ai",
                  content: "",
                  streaming: true,
                }) - 1;
            } else {
              this.messages[this.currentAiIndex].content = "";
              this.messages[this.currentAiIndex].streaming = true;
            }
            this.lastChunk = "";
            this.es = new EventSource(result.url);
            this.es.onopen = () => {
              console.log("SSE connection opened", result.url);
            };
            this.es.onmessage = (ev) => {
              if (!ev.data) return;
              const raw = String(ev.data);

              // Handle explicit end sentinel from server
              if (raw === "[DONE]" || raw === "__DONE__") {
                if (this.currentAiIndex !== -1) {
                  this.messages[this.currentAiIndex].streaming = false;
                  this.currentAiIndex = -1;
                }
                try {
                  this.es.close();
                } catch (_) {}
                this.es = null;
                return;
              }

              // 保留模型返回的换行和空格，方便前端按 Markdown 风格展示
              const chunk = raw;

              // repeated-chunk protection: if same chunk repeats too often, finalize
              if (chunk === this.lastChunk) {
                this.repeatCount = (this.repeatCount || 0) + 1;
                if (this.repeatCount > this.maxRepeatCount) {
                  if (this.currentAiIndex !== -1) {
                    this.messages[this.currentAiIndex].streaming = false;
                    this.currentAiIndex = -1;
                  }
                  try {
                    this.es.close();
                  } catch (_) {}
                  this.es = null;
                }
                return;
              }

              // new chunk
              this.repeatCount = 0;
              this.lastChunk = chunk;
              if (this.currentAiIndex !== -1) {
                const prev = this.messages[this.currentAiIndex].content || "";
                this.messages[this.currentAiIndex].content = prev + chunk;
              } else {
                this.currentAiIndex =
                  this.messages.push({
                    role: "ai",
                    content: chunk,
                    streaming: false,
                  }) - 1;
              }
              this.scrollToBottom();
            };
            this.es.onerror = (e) => {
              console.debug("SSE error, stop current answer", e);
              // 一问一答模式：任何错误都直接结束当前回答，不做自动重连，避免重复回答
              try {
                if (this.currentAiIndex !== -1) {
                  this.messages[this.currentAiIndex].streaming = false;
                  this.currentAiIndex = -1;
                }
              } catch (_) {}
              try {
                if (this.es) this.es.close();
              } catch (_) {}
              this.es = null;
              this.reconnectAttempts = 0;
              // 如有需要，可以给出简短提示；如果你不想看到这行，可以删掉下面 push
              this.messages.push({
                role: "ai",
                content: "[提示] 本次回答已结束，如需继续请重新提问。",
              });
            };
          } catch (e) {
            console.error("Failed to open EventSource", e);
            this.messages.push({
              role: "ai",
              content: "[错误] 无法建立 SSE 连接",
            });
            this.es = null;
          }
        } catch (e) {
          console.error("Error trying SSE candidate", e);
          this.messages.push({
            role: "ai",
            content: "[错误] 尝试 SSE 时发生异常",
          });
        }
      };

      trySequential();
    },
  },
};
</script>

<style scoped>
.chat-root {
  border: 1px solid #ddd;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  height: 70vh;
}
.chat-header {
  padding: 10px;
  border-bottom: 1px solid #eee;
  font-weight: 600;
}
.chat-body {
  padding: 12px;
  flex: 1;
  overflow: auto;
  background: #fafafa;
}
.chat-footer {
  padding: 10px;
  border-top: 1px solid #eee;
  display: flex;
}
.chat-footer input {
  flex: 1;
  padding: 8px;
  margin-right: 8px;
}
.msg {
  display: flex;
  margin-bottom: 8px;
}
.msg.user {
  justify-content: flex-end;
}
.msg.ai {
  justify-content: flex-start;
}
.msg-error .bubble {
  background: #ffe5e5;
  color: #b30000;
}
.msg-hint .bubble {
  background: #fff7e0;
  color: #8a6d00;
}
.bubble {
  max-width: 70%;
  padding: 8px 12px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}
.msg.user .bubble {
  background: #cfe9ff;
}
</style>
