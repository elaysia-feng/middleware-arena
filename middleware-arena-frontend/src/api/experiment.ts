import request from './request'

// 后端 CreateTemplateRequest 实际要求 name + middlewareType + scenario + files（不能空）
// tags 是单个 String（逗号分隔），runParams 是 Map<String,Object>
export interface TemplateFileRequest {
  path: string
  language: string
  content: string
  editable: boolean
}

export interface CreateTemplateRequest {
  name: string
  middlewareType: string // 后端必填
  scenario: string // 后端必填
  files: TemplateFileRequest[] // 后端必填且非空
  tags?: string // 逗号分隔
  description?: string
  runParams?: Record<string, unknown>
}

export interface UpdateTemplateRequest {
  name?: string
  middlewareType?: string
  scenario?: string
  tags?: string
  description?: string
}

// 后端 TemplateResponse 字段
export interface TemplateResponse {
  id: number
  name: string
  description?: string
  middlewareType: string
  scenario?: string
  tags?: string
  status?: string
  // 后端是 userId 不是 ownerId，没有 ownerName 联表
  userId: number
  createdAt: string
  updatedAt?: string
  latestVersionId?: number
  latestVersionNo?: number
}

// 后端 VersionResponse 字段：id, templateId, versionNo(Long), changeSummary, createdBy, createdAt
// filesJson / runParamsJson 仅在 owner 调 getVersion 时返回（ExperimentServiceImpl.toVersionResponse）
export interface VersionResponse {
  id: number
  templateId: number
  versionNo: number
  changeSummary?: string
  createdBy?: number
  createdAt: string
  filesJson?: string
  runParamsJson?: string
}

export interface FileDiff {
  path: string
  changeType: 'ADDED' | 'MODIFIED' | 'DELETED' | 'UNCHANGED' | string
  diffLines?: DiffLine[]
}

export interface DiffLine {
  type: 'ADD' | 'REMOVE' | 'CONTEXT' | string
  content: string
  lineNumber?: number
}

// 后端 VersionDiffResponse 字段
export interface VersionDiffResponse {
  fromVersionId: number
  fromVersionNo: number
  toVersionId: number
  toVersionNo: number
  fileDiffs: FileDiff[]
}

// 后端 TaskStatus 实际枚举：CREATED / QUEUED / RUNNING / SUCCESS / FAILED / CANCELLED
export type TaskStatus =
  | 'CREATED'
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELLED'
  | string

// 后端 TaskResponse 字段
export interface TaskResponse {
  id: number
  userId: number
  versionId: number
  status: TaskStatus
  currentStage?: string
  progress?: number // 0-100
  tierSnapshot?: string
  errorCode?: string
  errorMessage?: string
  createdAt?: string
  startedAt?: string
  finishedAt?: string
}

// ----- Templates -----
export function createTemplate(data: CreateTemplateRequest): Promise<TemplateResponse> {
  return request
    .post('/experiment/template', data)
    .then((r) => r as unknown as TemplateResponse)
}

export function createBuiltinTemplate(builtinKey: string): Promise<TemplateResponse> {
  return request
    .post(`/experiment/template/builtin/${builtinKey}`)
    .then((r) => r as unknown as TemplateResponse)
}

export function updateTemplate(
  templateId: number,
  data: UpdateTemplateRequest,
): Promise<TemplateResponse> {
  return request
    .put(`/experiment/template/${templateId}`, data)
    .then((r) => r as unknown as TemplateResponse)
}

export function deleteTemplate(templateId: number): Promise<void> {
  return request.delete(`/experiment/template/${templateId}`).then(() => undefined)
}

export function getTemplate(templateId: number): Promise<TemplateResponse> {
  return request
    .get(`/experiment/template/${templateId}`)
    .then((r) => r as unknown as TemplateResponse)
}

export function pageTemplates(page = 1, size = 12): Promise<TemplateResponse[]> {
  return request
    .get('/experiment/template/page', { params: { page, size } })
    .then((r) => r as unknown as TemplateResponse[])
}

// ----- Versions -----
// 后端 POST /experiment/version 接受 JSON 请求体：templateId / filesJson / runParamsJson / changeSummary
// 注意：filesJson 是真正的实验文件清单 (List<FilePatch>)，不是任意 JSON
export interface CreateVersionParams {
  templateId: number
  filesJson: string
  runParamsJson: string
  changeSummary?: string
}

export function createVersion(params: CreateVersionParams): Promise<VersionResponse> {
  return request
    .post('/experiment/version', params)
    .then((r) => r as unknown as VersionResponse)
}

// 服务端 rollbackVersion 实际语义：复制前一版本为新 V(n+1)，不是真把 latestVersionId 改成目标版
export function rollbackVersion(templateId: number, versionId: number): Promise<void> {
  return request
    .post('/experiment/version/rollback', undefined, { params: { templateId, versionId } })
    .then(() => undefined)
}

export function listVersions(templateId: number): Promise<VersionResponse[]> {
  return request
    .get('/experiment/version/list', { params: { templateId } })
    .then((r) => r as unknown as VersionResponse[])
}

export function getVersion(versionId: number): Promise<VersionResponse> {
  return request
    .get(`/experiment/version/${versionId}`)
    .then((r) => r as unknown as VersionResponse)
}

export function diffVersions(
  fromVersionId: number,
  toVersionId: number,
): Promise<VersionDiffResponse> {
  return request
    .get('/experiment/version/diff', { params: { fromVersionId, toVersionId } })
    .then((r) => r as unknown as VersionDiffResponse)
}

// ----- Tasks -----
// 后端 POST /experiment/task @RequestParam Long versionId；只有模板 owner 可创建；FREE 用户只能跑 REDIS
export function createTask(versionId: number): Promise<TaskResponse> {
  return request
    .post('/experiment/task', undefined, { params: { versionId } })
    .then((r) => r as unknown as TaskResponse)
}

export function getTask(taskId: number): Promise<TaskResponse> {
  return request
    .get(`/experiment/task/${taskId}`)
    .then((r) => r as unknown as TaskResponse)
}

export function pageTasks(page = 1, size = 20): Promise<TaskResponse[]> {
  return request
    .get('/experiment/task/page', { params: { page, size } })
    .then((r) => r as unknown as TaskResponse[])
}

export function cancelTask(taskId: number): Promise<void> {
  return request.post(`/experiment/task/${taskId}/cancel`).then(() => undefined)
}

// 后端 retryTask 只允许 status == FAILED，其他状态会抛 PARAM_INVALID
export function retryTask(taskId: number): Promise<TaskResponse> {
  return request
    .post(`/experiment/task/${taskId}/retry`)
    .then((r) => r as unknown as TaskResponse)
}

// 后端 getTaskProgress 返回短中文状态标签（"排队中"/"构建中"/"压测中"/...），不是日志流
export function getTaskProgress(taskId: number): Promise<string> {
  return request
    .get(`/experiment/task/${taskId}/progress`)
    .then((r) => (typeof r === 'string' ? r : String(r ?? '')))
}
