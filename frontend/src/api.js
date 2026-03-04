import axios from "axios";

// 统一从环境变量读取后端地址，便于开发 / 测试 / 生产环境切换
// 在开发环境（Vite）下，可在 .env.development 中配置：
// VITE_API_BASE=http://localhost:8123/api
const baseURL =
  (typeof import.meta !== "undefined" &&
    import.meta.env &&
    import.meta.env.VITE_API_BASE) ||
  "/api";

const instance = axios.create({
  baseURL,
  timeout: 30000,
});

export default instance;
