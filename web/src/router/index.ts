import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { flattenMenu, menuTree } from './menu'
import { useUserStore } from '@/stores/user'

const Placeholder = () => import('@/views/Placeholder.vue')

/** 已交付页面，其余菜单路径统一指向占位页 */
const builtPaths = new Set([
  '/home',
  '/master/material',
  '/master/supplier',
  '/master/warehouse',
  '/master/dict',
  '/request',
  '/request/todo',
  '/order',
  '/order/todo',
  '/arrival',
  '/arrival/register',
  '/inbound',
  '/stock',
  '/stock/flow',
  '/price',
  '/price/deviation',
  '/system/users',
  '/system/roles',
  '/system/approval',
  '/system/params',
  '/system/kb',
  '/system/logs',
  '/system/dashboard',
  '/assistant',
  '/assistant/audit'
])

const placeholderRoutes: RouteRecordRaw[] = flattenMenu(menuTree)
  .filter((node) => node.path && !builtPaths.has(node.path))
  .map((node) => ({
    path: node.path!.slice(1),
    name: node.path!.slice(1).replace(/\//g, '-'),
    component: Placeholder,
    meta: { title: node.title, perm: node.perm }
  }))

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'master/material',
        name: 'master-material',
        component: () => import('@/views/business/master/MaterialList.vue'),
        meta: { title: '物料档案', perm: 'm1:material:list' }
      },
      {
        path: 'master/supplier',
        name: 'master-supplier',
        component: () => import('@/views/business/master/SupplierList.vue'),
        meta: { title: '供应商档案', perm: 'm1:supplier:list' }
      },
      {
        path: 'master/warehouse',
        name: 'master-warehouse',
        component: () => import('@/views/business/master/WarehouseList.vue'),
        meta: { title: '仓库与库位', perm: 'm1:warehouse:list' }
      },
      {
        path: 'master/dict',
        name: 'master-dict',
        component: () => import('@/views/business/master/DictList.vue'),
        meta: { title: '业务字典', perm: 'm1:dict:list' }
      },
      {
        path: 'request',
        name: 'request-list',
        component: () => import('@/views/business/RequestList.vue'),
        meta: { title: '采购申请', perm: 'm2:request:list' }
      },
      {
        path: 'request/todo',
        name: 'request-todo',
        component: () => import('@/views/business/RequestTodo.vue'),
        meta: { title: '我的待办', perm: 'm2:request:todo' }
      },
      {
        path: 'order',
        name: 'order-list',
        component: () => import('@/views/business/OrderList.vue'),
        meta: { title: '采购订单', perm: 'm3:order:list' }
      },
      {
        path: 'order/todo',
        name: 'order-todo',
        component: () => import('@/views/business/OrderTodo.vue'),
        meta: { title: '订单待办', perm: 'm3:order:todo' }
      },
      {
        path: 'arrival',
        name: 'arrival-list',
        component: () => import('@/views/business/ArrivalList.vue'),
        meta: { title: '到货验收', perm: 'm4:inspection:list' }
      },
      {
        path: 'arrival/register',
        name: 'arrival-register',
        component: () => import('@/views/business/ArrivalRegister.vue'),
        meta: { title: '验收登记', perm: 'm4:inspection:create' }
      },
      {
        path: 'inbound',
        name: 'inbound-list',
        component: () => import('@/views/business/InboundList.vue'),
        meta: { title: '采购入库', perm: 'm5:inbound:list' }
      },
      {
        path: 'stock',
        name: 'stock-list',
        component: () => import('@/views/business/StockList.vue'),
        meta: { title: '库存台账', perm: 'm5:stock:list' }
      },
      {
        path: 'stock/flow',
        name: 'stock-flow',
        component: () => import('@/views/business/FlowList.vue'),
        meta: { title: '库存流水', perm: 'm5:flow:list' }
      },
      {
        path: 'price',
        name: 'price',
        component: () => import('@/views/business/PriceList.vue'),
        meta: { title: '价格参考', perm: 'm6:price:list' }
      },
      {
        path: 'price/deviation',
        name: 'price-deviation',
        component: () => import('@/views/business/DeviationList.vue'),
        meta: { title: '价格偏离关注', perm: 'm6:stat:view' }
      },
      {
        path: 'system/users',
        name: 'system-users',
        component: () => import('@/views/system/UserList.vue'),
        meta: { title: '用户管理', perm: 'm11:user:list' }
      },
      {
        path: 'system/roles',
        name: 'system-roles',
        component: () => import('@/views/system/RoleList.vue'),
        meta: { title: '角色权限', perm: 'm11:role:list' }
      },
      {
        path: 'system/approval',
        name: 'system-approval',
        component: () => import('@/views/system/ApprovalConfig.vue'),
        meta: { title: '审批配置', perm: 'm11:approval_node:list' }
      },
      {
        path: 'system/params',
        name: 'system-params',
        component: () => import('@/views/system/ParamConfig.vue'),
        meta: { title: '参数配置', perm: 'm11:param:list' }
      },
      {
        path: 'system/kb',
        name: 'system-kb',
        component: () => import('@/views/system/KbManage.vue'),
        meta: { title: '知识库', perm: 'm10:document:list' }
      },
      {
        path: 'system/logs',
        name: 'system-logs',
        component: () => import('@/views/system/LogAudit.vue'),
        meta: { title: '日志审计', perm: 'm11:log:list' }
      },
      {
        path: 'system/dashboard',
        name: 'system-dashboard',
        component: () => import('@/views/business/dashboard/Dashboard.vue'),
        meta: { title: '运营看板', perm: 'm12:summary:purchase' }
      },
      {
        path: 'assistant',
        name: 'assistant',
        component: () => import('@/views/business/ai/AssistantChat.vue'),
        meta: { title: '助手对话', perm: 'm7:chat:query' }
      },
      {
        path: 'assistant/audit',
        name: 'assistant-audit',
        component: () => import('@/views/business/ai/AssistantAudit.vue'),
        meta: { title: '查询审计', perm: 'm9:audit:list' }
      },
      {
        path: '403',
        name: 'forbidden',
        component: () => import('@/views/Forbidden.vue'),
        meta: { title: '无权限' }
      },
      ...placeholderRoutes
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  document.title = to.meta.title ? `${to.meta.title} - 采购管理系统` : '采购管理系统'
  if (to.meta.public) {
    return true
  }
  const store = useUserStore()
  if (!store.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!store.loaded) {
    try {
      await store.fetchMe()
    } catch {
      store.clear()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (to.meta.perm && !store.hasPerm(to.meta.perm as string)) {
    return { path: '/403' }
  }
  return true
})

export default router
