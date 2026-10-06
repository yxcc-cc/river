import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/bigscreen',
    name: 'BigScreen',
    component: () => import('../views/BigScreen.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '实时数据看板', icon: 'DataBoard' }
      },
      {
        path: 'alerts',
        name: 'Alerts',
        component: () => import('../views/Alerts.vue'),
        meta: { title: '预警记录管理', icon: 'Bell' }
      },
      {
        path: 'data',
        name: 'DataHistory',
        component: () => import('../views/DataHistory.vue'),
        meta: { title: '历史数据查询', icon: 'TrendCharts' }
      },
      {
        path: 'monitor',
        name: 'Monitor',
        component: () => import('../views/Monitor.vue'),
        meta: { title: '实时监控', icon: 'VideoCamera' }
      },
      {
        path: 'map',
        name: 'MapPage',
        component: () => import('../views/MapPage.vue'),
        meta: { title: '地图总览', icon: 'Location' }
      },
      {
        path: 'attendance',
        name: 'Attendance',
        component: () => import('../views/Attendance.vue'),
        meta: { title: '工作考勤', icon: 'Clock' }
      },
      {
        path: 'devices',
        name: 'Devices',
        component: () => import('../views/Devices.vue'),
        meta: { title: '设备管理', icon: 'Monitor', adminOnly: true }
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('../views/Users.vue'),
        meta: { title: '用户管理', icon: 'User', adminOnly: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Navigation guard
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')

  if (to.meta.public) {
    if (token && to.path === '/login') {
      next('/dashboard')
    } else {
      next()
    }
    return
  }

  if (!token) {
    next('/login')
    return
  }

  // Check admin-only routes
  if (to.meta.adminOnly) {
    const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
    if (userInfo.roleKey !== 'admin') {
      next('/dashboard')
      return
    }
  }

  next()
})

export default router
