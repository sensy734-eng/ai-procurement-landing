import request, { downloadCsv } from './request'

/**
 * M1 基础数据管理接口封装。
 * 每个函数标注对应功能点编号，便于论文第 5 章按编号引用。
 * 页面编号对应功能模块清单 P1 物料、P2 供应商、P3 仓库库位、P4 字典。
 */

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

// ---------------- M1-1 物料 ----------------

export interface MaterialExtAttr {
  id?: number
  attrName: string
  attrValue: string
  sortNo?: number
}

export interface MaterialRow {
  id: number
  materialNo: string
  materialName: string
  categoryId: number
  categoryNo: string
  categoryName: string
  spec: string
  brand: string
  unitId: number
  unitCode: string
  unitName: string
  materialType: number
  materialTypeName: string
  safetyStock: number
  status: number
  remark: string
  extAttrs: MaterialExtAttr[]
  createdAt: string
}

export interface CategoryNode {
  id: number
  categoryNo: string
  categoryName: string
  parentId: number
  levelNo: number
  sortNo: number
  status: number
  remark: string
  children: CategoryNode[]
}

export interface ImportRow {
  lineNo: number
  materialNo: string
  success: boolean
  action: string
  message: string
}

export interface ImportResult {
  totalCount: number
  successCount: number
  failCount: number
  rows: ImportRow[]
}

/** M1-1-1 分页列表 */
export function pageMaterials(params: Record<string, unknown>) {
  return request.get<never, PageResult<MaterialRow>>('/m1/materials/page', { params })
}

/** M1-1-1 详情，含扩展属性 */
export function getMaterial(id: number) {
  return request.get<never, MaterialRow>(`/m1/materials/${id}`)
}

/** M1-1-1 可选物料，仅启用 */
export function materialOptions() {
  return request.get<never, MaterialRow[]>('/m1/materials/options')
}

/** M1-1-2 新增物料 */
export function createMaterial(body: Record<string, unknown>) {
  return request.post<never, number>('/m1/materials', body)
}

/** M1-1-3 编辑物料 */
export function updateMaterial(id: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m1/materials/${id}`, body)
}

/** M1-1-4 启用停用 */
export function toggleMaterialStatus(id: number) {
  return request.patch<never, void>(`/m1/materials/${id}/status`)
}

/** M1-1-5 批量导入，返回逐行明细 */
export function importMaterials(file: File) {
  const form = new FormData()
  form.append('file', file)
  return request.post<never, ImportResult>('/m1/materials/import', form)
}

/** M1-1-5 导出地址 */
export function materialExportUrl(params: Record<string, unknown>) {
  const query = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&')
  return `/api/m1/materials/export${query ? '?' + query : ''}`
}

/** M1-1-5 导入模板下载 */
export function downloadMaterialTemplate() {
  return downloadCsv('/api/m1/materials/import-template', '物料导入模板.csv')
}

// ---------------- M1-1-7 物料分类 ----------------

/** M1-1-7 分类树 */
export function categoryTree() {
  return request.get<never, CategoryNode[]>('/m1/material-categories/tree')
}

/** M1-1-7 新增分类 */
export function createCategory(body: Record<string, unknown>) {
  return request.post<never, number>('/m1/material-categories', body)
}

/** M1-1-7 编辑分类 */
export function updateCategory(id: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m1/material-categories/${id}`, body)
}

// ---------------- M1-2 供应商 ----------------

export interface ContactRow {
  id?: number
  contactName: string
  position: string
  phone: string
  email: string
  isDefault: number
  status: number
  remark: string
}

export interface ScopeRow {
  id?: number
  scopeType: number
  scopeId: number
  scopeName: string
  remark: string
}

export interface AttachmentRow {
  id: number
  fileName: string
  fileType: string
  fileSize: number
  expireDate: string
  remark: string
  createdAt: string
}

export interface SupplierRow {
  id: number
  supplierNo: string
  supplierName: string
  supplierType: number
  supplierTypeName: string
  taxNo: string
  address: string
  phone: string
  email: string
  bankName: string
  bankAccount: string
  status: number
  remark: string
  contactCount: number
  defaultContactName: string
  scopeCount: number
  attachmentCount: number
  createdAt: string
}

/** M1-2-1 分页列表 */
export function pageSuppliers(params: Record<string, unknown>) {
  return request.get<never, PageResult<SupplierRow>>('/m1/suppliers/page', { params })
}

/** M1-2-1 详情 */
export function getSupplier(id: number) {
  return request.get<never, SupplierRow>(`/m1/suppliers/${id}`)
}

/** M1-2-1 可选供应商，仅启用 */
export function supplierOptions() {
  return request.get<never, SupplierRow[]>('/m1/suppliers/options')
}

/** M1-2-2 新增供应商 */
export function createSupplier(body: Record<string, unknown>) {
  return request.post<never, number>('/m1/suppliers', body)
}

/** M1-2-2 编辑供应商 */
export function updateSupplier(id: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m1/suppliers/${id}`, body)
}

/** M1-2-3 启用停用 */
export function toggleSupplierStatus(id: number) {
  return request.patch<never, void>(`/m1/suppliers/${id}/status`)
}

/** M1-2-4 联系人列表 */
export function supplierContacts(id: number) {
  return request.get<never, ContactRow[]>(`/m1/suppliers/${id}/contacts`)
}

/** M1-2-4 联系人整体保存 */
export function saveSupplierContacts(id: number, contacts: ContactRow[]) {
  return request.put<never, void>(`/m1/suppliers/${id}/contacts`, { contacts })
}

/** M1-2-6 供货范围列表 */
export function supplierScopes(id: number) {
  return request.get<never, ScopeRow[]>(`/m1/suppliers/${id}/scopes`)
}

/** M1-2-6 供货范围整体保存 */
export function saveSupplierScopes(id: number, scopes: { scopeType: number; scopeId: number; remark: string }[]) {
  return request.put<never, void>(`/m1/suppliers/${id}/scopes`, { scopes })
}

/** M1-2-5 资质附件列表 */
export function supplierAttachments(id: number) {
  return request.get<never, AttachmentRow[]>(`/m1/suppliers/${id}/attachments`)
}

/** M1-2-5 上传资质附件 */
export function uploadSupplierAttachment(id: number, form: FormData) {
  return request.post<never, number>(`/m1/suppliers/${id}/attachments`, form)
}

/** M1-2-5 下载地址 */
export function attachmentDownloadUrl(attachmentId: number) {
  return `/api/m1/suppliers/attachments/${attachmentId}/download`
}

/** M1-2-5 删除附件记录 */
export function deleteSupplierAttachment(attachmentId: number) {
  return request.delete<never, void>(`/m1/suppliers/attachments/${attachmentId}`)
}

// ---------------- M1-3 仓库 ----------------

export interface WarehouseRow {
  id: number
  warehouseNo: string
  warehouseName: string
  managerId: number
  managerName: string
  address: string
  phone: string
  status: number
  remark: string
  locationCount: number
  userCount: number
}

export interface LocationRow {
  id: number
  warehouseId: number
  locationNo: string
  locationName: string
  status: number
  remark: string
}

export interface WarehouseUserRow {
  userId: number
  username: string
  realName: string
  deptName: string
  isDefault: number
}

/** M1-3-1 分页列表 */
export function pageWarehouses(params: Record<string, unknown>) {
  return request.get<never, PageResult<WarehouseRow>>('/m1/warehouses/page', { params })
}

/** M1-3-1 可选仓库，仅启用 */
export function warehouseOptions() {
  return request.get<never, WarehouseRow[]>('/m1/warehouses/options')
}

/** M1-3-1 新增仓库 */
export function createWarehouse(body: Record<string, unknown>) {
  return request.post<never, number>('/m1/warehouses', body)
}

/** M1-3-1 编辑仓库 */
export function updateWarehouse(id: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m1/warehouses/${id}`, body)
}

/** M1-3-2 库位列表 */
export function warehouseLocations(id: number) {
  return request.get<never, LocationRow[]>(`/m1/warehouses/${id}/locations`)
}

/** M1-3-2 新增库位 */
export function createWarehouseLocation(warehouseId: number, body: Record<string, unknown>) {
  return request.post<never, number>(`/m1/warehouses/${warehouseId}/locations`, body)
}

/** M1-3-2 编辑库位 */
export function updateWarehouseLocation(locationId: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m1/warehouses/locations/${locationId}`, body)
}

/** M1-3-3 仓库使用人列表 */
export function warehouseUsers(id: number) {
  return request.get<never, WarehouseUserRow[]>(`/m1/warehouses/${id}/users`)
}

/** M1-3-3 仓库使用人整体保存 */
export function saveWarehouseUsers(id: number, userIds: number[], defaultUserId: number | null) {
  return request.put<never, void>(`/m1/warehouses/${id}/users`, { userIds, defaultUserId })
}

// ---------------- M1-4 字典 ----------------

export interface DictTypeRow {
  id: number
  typeCode: string
  typeName: string
  status: number
  remark: string
  itemCount: number
}

export interface DictItemRow {
  id: number
  typeId: number
  itemCode: string
  itemName: string
  parentId: number
  sortNo: number
  status: number
  extValue: string
  remark: string
}

/** 下拉选项，仅启用项 */
export function dictItems(typeCode: string) {
  return request.get<never, DictItemRow[]>(`/m1/dicts/${typeCode}/items`)
}

/** M1-4-1 字典类型列表 */
export function dictTypes() {
  return request.get<never, DictTypeRow[]>('/m1/dicts/types')
}

/** M1-4-1 新增字典类型 */
export function createDictType(body: Record<string, unknown>) {
  return request.post<never, number>('/m1/dicts/types', body)
}

/** M1-4-1 编辑字典类型 */
export function updateDictType(id: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m1/dicts/types/${id}`, body)
}

/** M1-4-1 管理端全量字典项，含停用 */
export function dictItemsAdmin(typeCode: string) {
  return request.get<never, DictItemRow[]>(`/m1/dicts/types/${typeCode}/items`)
}

/** M1-4-1、M1-4-2 新增字典项 */
export function createDictItem(body: Record<string, unknown>) {
  return request.post<never, number>('/m1/dicts/items', body)
}

/** M1-4-1、M1-4-2 编辑字典项 */
export function updateDictItem(id: number, body: Record<string, unknown>) {
  return request.put<never, void>(`/m1/dicts/items/${id}`, body)
}
