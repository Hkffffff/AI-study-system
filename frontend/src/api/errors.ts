/** Field-level validation error, mirrors backend FieldErrorItem. */
export interface FieldError {
  field: string
  message: string
}

/** Normalised error shown to the user; built from the backend's RFC 7807 ProblemDetail. */
export interface ApiError {
  status: number | null
  code: string
  message: string
  errors: FieldError[]
}

interface ProblemDetailLike {
  status?: unknown
  code?: unknown
  title?: unknown
  detail?: unknown
  errors?: unknown
}

interface AxiosLikeError {
  message?: string
  code?: string
  response?: { status?: number; data?: unknown }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function asString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() !== '' ? value : undefined
}

function parseFieldErrors(value: unknown): FieldError[] {
  if (!Array.isArray(value)) return []
  return value
    .filter(isObject)
    .map((item) => ({ field: String(item.field ?? ''), message: String(item.message ?? '') }))
}

/** Converts anything thrown by axios (or elsewhere) into an {@link ApiError}. Pure; no UI side effects. */
export function toApiError(error: unknown): ApiError {
  const err = (isObject(error) ? error : {}) as AxiosLikeError
  const response = err.response

  if (!response) {
    return {
      status: null,
      code: 'NETWORK_ERROR',
      message: '无法连接后端服务，请确认后端已启动',
      errors: [],
    }
  }

  const status = typeof response.status === 'number' ? response.status : null
  const body = (isObject(response.data) ? response.data : {}) as ProblemDetailLike
  const errors = parseFieldErrors(body.errors)
  const firstError = errors[0]
  const firstFieldMessage = firstError ? `${firstError.field}: ${firstError.message}` : undefined

  return {
    status,
    code: asString(body.code) ?? (status !== null && status >= 500 ? 'INTERNAL_ERROR' : 'UNKNOWN'),
    message:
      firstFieldMessage ??
      asString(body.detail) ??
      asString(body.title) ??
      (status !== null ? `请求失败（HTTP ${status}）` : '请求失败'),
    errors,
  }
}
