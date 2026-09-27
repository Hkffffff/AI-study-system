import axios, { type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { toApiError } from './errors'

declare module 'axios' {
  interface AxiosRequestConfig {
    /** Skip the global error toast (caller handles the error itself). */
    silent?: boolean
  }
}

/** Single axios instance; all calls go to the local backend via /api (never to AI providers). */
export const http = axios.create({
  baseURL: '/api',
  timeout: 30_000,
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const apiError = toApiError(error)
    const config = (error?.config ?? {}) as AxiosRequestConfig
    if (!config.silent) {
      ElMessage.error(apiError.message)
    }
    return Promise.reject(apiError)
  },
)
