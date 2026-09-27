import { describe, expect, it } from 'vitest'
import { toApiError } from './errors'

describe('toApiError', () => {
  it('reports network errors when there is no response', () => {
    const error = toApiError({ message: 'Network Error' })
    expect(error.status).toBeNull()
    expect(error.code).toBe('NETWORK_ERROR')
  })

  it('uses code and detail from a ProblemDetail body', () => {
    const error = toApiError({
      response: {
        status: 404,
        data: { status: 404, code: 'NOT_FOUND', title: '资源不存在', detail: '题目 42 不存在' },
      },
    })
    expect(error).toEqual({ status: 404, code: 'NOT_FOUND', message: '题目 42 不存在', errors: [] })
  })

  it('surfaces the first field error for validation failures', () => {
    const error = toApiError({
      response: {
        status: 400,
        data: {
          code: 'VALIDATION_FAILED',
          detail: '参数校验失败',
          errors: [{ field: 'name', message: '不能为空' }],
        },
      },
    })
    expect(error.code).toBe('VALIDATION_FAILED')
    expect(error.message).toBe('name: 不能为空')
    expect(error.errors).toHaveLength(1)
  })

  it('falls back gracefully when the body is not a ProblemDetail', () => {
    const error = toApiError({ response: { status: 502, data: '<html>Bad Gateway</html>' } })
    expect(error.code).toBe('INTERNAL_ERROR')
    expect(error.message).toBe('请求失败（HTTP 502）')
  })
})
