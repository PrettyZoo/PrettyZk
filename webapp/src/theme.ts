import type { GlobalThemeOverrides } from 'naive-ui'
import { darkTheme } from 'naive-ui'

const common = {
  primaryColor: '#0F9D8A',
  primaryColorHover: '#0C8A79',
  primaryColorPressed: '#0A7567',
  primaryColorSuppl: '#0F9D8A',
  borderRadius: '6px',
  fontSize: '13px',
  fontFamily:
    '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
  fontFamilyMono: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
}

export const lightThemeOverrides: GlobalThemeOverrides = {
  common,
  Button: { borderRadiusMedium: '6px', borderRadiusSmall: '5px' },
  Input: { borderRadius: '6px' },
  Card: { borderRadius: '8px' },
}

export const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    ...common,
    primaryColor: '#4FD1C5',
    primaryColorHover: '#6EE7DB',
    primaryColorPressed: '#3BB8AD',
    primaryColorSuppl: '#4FD1C5',
  },
  Button: { borderRadiusMedium: '6px', borderRadiusSmall: '5px' },
  Input: { borderRadius: '6px' },
  Card: { borderRadius: '8px' },
}

export function naiveTheme(isDark: boolean) {
  return isDark ? darkTheme : null
}
