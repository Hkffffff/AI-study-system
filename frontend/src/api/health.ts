import { http } from './http'

export interface HealthStatus {
  status: 'UP' | 'DEGRADED'
  database: 'UP' | 'DOWN'
  time: string
}

export async function fetchHealth(): Promise<HealthStatus> {
  // 503 still carries a HealthStatus body, so accept it instead of treating it as an error.
  const { data } = await http.get<HealthStatus>('/health', {
    silent: true,
    validateStatus: (status) => status === 200 || status === 503,
  })
  return data
}
