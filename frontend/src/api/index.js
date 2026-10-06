import request from '../utils/request'

// Auth
export const login = data => request.post('/auth/login', data)

// Devices
export const getDevices = () => request.get('/devices')
export const getDevice = id => request.get(`/devices/${id}`)
export const addDevice = data => request.post('/devices', data)
export const updateDevice = (id, data) => request.put(`/devices/${id}`, data)
export const deleteDevice = id => request.delete(`/devices/${id}`)
export const updateThreshold = (id, data) => request.put(`/devices/${id}/threshold`, data)

// Alerts
export const getAlerts = params => request.get('/alerts', { params })
export const handleAlert = (id, data) => request.put(`/alerts/${id}/handle`, data)
export const getUnhandledCount = () => request.get('/alerts/count-unhandled')
export const deleteAlert = id => request.delete(`/alerts/${id}`)
export const clearAlerts = status => request.delete('/alerts/clear', { params: { status } })

// Data
export const getRealtimeData = deviceId => request.get('/data/realtime', { params: { deviceId } })
export const getWaterHistory = params => request.get('/data/water', { params })
export const getWaterTrend = (deviceId, hours = 24) => request.get('/data/water/trend', { params: { deviceId, hours } })

// Users
export const getUsers = params => request.get('/users', { params })
export const getUser = id => request.get(`/users/${id}`)
export const addUser = data => request.post('/users', data)
export const updateUser = (id, data) => request.put(`/users/${id}`, data)
export const deleteUser = id => request.delete(`/users/${id}`)
export const resetPassword = (id, data) => request.put(`/users/${id}/password`, data)

// Statistics
export const getOverview = () => request.get('/statistics/overview')

// Monitor (Vision Detection)
export const getLatestImage = deviceId => request.get('/hardware/image/latest', { params: { deviceId } })
export const getCaptureList = (deviceId, limit = 10) => request.get('/hardware/image/list', { params: { deviceId, limit } })
export const deleteCapture = url => request.delete('/hardware/image/delete', { params: { url } })
export const clearCaptures = deviceId => request.delete('/hardware/image/clear', { params: { deviceId } })

// Attendance
export const checkIn = data => request.post('/attendance/check-in', data)
export const checkOut = data => request.post('/attendance/check-out', data)
export const getTodayAttendance = () => request.get('/attendance/today')
export const getAttendanceRecords = params => request.get('/attendance/records', { params })
export const getAttendanceStats = params => request.get('/attendance/statistics', { params })

// Face Service (port 8084, direct calls)
const FACE_SERVICE_URL = 'http://localhost:8084'

export const registerFace = async (userId, imageBase64) => {
  const res = await fetch(`${FACE_SERVICE_URL}/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId, image: imageBase64 })
  })
  return res.json()
}

export const checkFaceRegistered = async (userId) => {
  const res = await fetch(`${FACE_SERVICE_URL}/check-register?userId=${userId}`)
  return res.json()
}

export const getFaceServiceStatus = async () => {
  const res = await fetch(`${FACE_SERVICE_URL}/status`)
  return res.json()
}
