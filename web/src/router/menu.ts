/**
 * 菜单定义。每项绑定动作级权限点编码，无权限的菜单不渲染。
 * 页面编号对应功能模块清单 P1~P26，未交付页面的路由指向占位页。
 */
export interface MenuNode {
  title: string
  path?: string
  icon?: string
  perm?: string
  children?: MenuNode[]
}

export const menuTree: MenuNode[] = [
  { title: 'AI 工作台', path: '/home', icon: 'HomeFilled' },
  {
    title: 'AI 能力',
    icon: 'ChatDotRound',
    children: [
      { title: 'AI 对话', path: '/assistant', perm: 'm7:chat:query' },
      { title: '知识库与检索实验', path: '/system/kb', perm: 'm10:document:list' },
      { title: '查询审计', path: '/assistant/audit', perm: 'm9:audit:list' }
    ]
  },
  {
    title: '基础数据',
    icon: 'Files',
    children: [
      { title: '物料档案', path: '/master/material', perm: 'm1:material:list' },
      { title: '供应商档案', path: '/master/supplier', perm: 'm1:supplier:list' },
      { title: '仓库与库位', path: '/master/warehouse', perm: 'm1:warehouse:list' },
      { title: '业务字典', path: '/master/dict', perm: 'm1:dict:list' }
    ]
  },
  {
    title: '采购业务',
    icon: 'ShoppingCart',
    children: [
      { title: '采购申请', path: '/request', perm: 'm2:request:list' },
      { title: '我的待办', path: '/request/todo', perm: 'm2:request:todo' },
      { title: '采购订单', path: '/order', perm: 'm3:order:list' },
      { title: '订单待办', path: '/order/todo', perm: 'm3:order:todo' },
      { title: '到货验收', path: '/arrival', perm: 'm4:inspection:list' },
      { title: '采购入库', path: '/inbound', perm: 'm5:inbound:list' },
      { title: '库存台账', path: '/stock', perm: 'm5:stock:list' },
      { title: '库存流水', path: '/stock/flow', perm: 'm5:flow:list' },
      { title: '价格参考', path: '/price', perm: 'm6:price:list' },
      { title: '价格偏离关注', path: '/price/deviation', perm: 'm6:stat:view' }
    ]
  },
  {
    title: '系统管理',
    icon: 'Setting',
    children: [
      { title: '用户管理', path: '/system/users', perm: 'm11:user:list' },
      { title: '角色权限', path: '/system/roles', perm: 'm11:role:list' },
      { title: '审批配置', path: '/system/approval', perm: 'm11:approval_node:list' },
      { title: '参数配置', path: '/system/params', perm: 'm11:param:list' },
      { title: '日志审计', path: '/system/logs', perm: 'm11:log:list' },
      { title: '运营看板', path: '/system/dashboard', perm: 'm12:summary:purchase' }
    ]
  }
]

export function flattenMenu(nodes: MenuNode[]): MenuNode[] {
  const result: MenuNode[] = []
  for (const node of nodes) {
    if (node.path) {
      result.push(node)
    }
    if (node.children) {
      result.push(...flattenMenu(node.children))
    }
  }
  return result
}
