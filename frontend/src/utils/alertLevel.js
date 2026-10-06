/**
 * 水位预警等级工具
 * 根据水位与阈值的比例划分 4 个等级
 */

// 预警等级定义
export const ALERT_LEVELS = {
  NORMAL: { key: 'normal', label: '正常', color: '#67c23a', bgColor: '#f0f9eb' },
  NOTICE: { key: 'notice', label: '注意', color: '#409eff', bgColor: '#ecf5ff' },
  WARNING: { key: 'warning', label: '警告', color: '#e6a23c', bgColor: '#fdf6ec' },
  DANGER: { key: 'danger', label: '危险', color: '#f56c6c', bgColor: '#fef0f0' }
}

/**
 * 根据水位和阈值获取预警等级
 * @param {number} waterLevel 当前水位
 * @param {number} threshold 报警阈值
 * @returns {object} 预警等级对象
 */
export function getAlertLevel(waterLevel, threshold) {
  if (!waterLevel || !threshold || threshold <= 0) {
    return ALERT_LEVELS.NORMAL
  }

  const ratio = waterLevel / threshold

  if (ratio >= 1.0) return ALERT_LEVELS.DANGER    // 超过阈值 = 危险
  if (ratio >= 0.85) return ALERT_LEVELS.WARNING   // 85%以上 = 警告
  if (ratio >= 0.7) return ALERT_LEVELS.NOTICE     // 70%以上 = 注意
  return ALERT_LEVELS.NORMAL                        // 70%以下 = 正常
}

/**
 * 获取预警等级对应的 Tag 类型（Element Plus）
 */
export function getAlertTagType(level) {
  const map = {
    normal: 'success',
    notice: '',
    warning: 'warning',
    danger: 'danger'
  }
  return map[level.key] || 'info'
}
