import request from './request'

export interface AnalyzeResponse {
  taskId: number
  analysisId?: number
  status: string
  traceId?: string
  result: Record<string, unknown>
}

export interface ResourceBudget {
  cpus: number
  memory_mb: number
}

export interface ResourceAdviceResponse {
  experiment_type: string
  final_budget: ResourceBudget
  llm_used: boolean
}

export function analyzeTask(taskId: number, baselineTaskId?: number): Promise<AnalyzeResponse> {
  return request.post('/agent/analyze', {
    taskId,
    ...(baselineTaskId ? { baselineTaskId } : {}),
  })
}

export function getResourceAdvice(data: {
  experiment_type: string
  run_params: Record<string, unknown>
  rule_budget: ResourceBudget
  max_budget: ResourceBudget
}): Promise<ResourceAdviceResponse> {
  return request.post('/agent/resource/advice', data)
}
