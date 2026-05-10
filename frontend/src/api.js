import axios from 'axios'

const baseURL =
  (typeof import.meta !== 'undefined' &&
    import.meta.env &&
    import.meta.env.VITE_API_BASE) ||
  '/api'

const instance = axios.create({
  baseURL,
  timeout: 30000
})

export default instance
