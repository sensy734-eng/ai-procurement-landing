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
  '/system/users',
  '/system/roles',
  '/system/approval',
  '/system/params',
  '/system/logs'
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
        path: 'system/logs',
        name: 'system-logs',
        component: () => import('@/views/system/LogAudit.vue'),
        meta: { title: '日志审计', perm: 'm11:log:list' }
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
