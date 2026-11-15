import { computed } from 'vue'
import { createDiscreteApi, darkTheme } from 'naive-ui'
import { state } from '../state.ts'
import { darkThemeOverrides, lightThemeOverrides } from '../theme.ts'
import { t } from '../i18n.ts'

const configProviderProps = computed(() => ({
  theme: state.config.theme === 'dark' ? darkTheme : null,
  themeOverrides:
    state.config.theme === 'dark' ? darkThemeOverrides : lightThemeOverrides,
}))

const { message, dialog } = createDiscreteApi(['message', 'dialog'], {
  configProviderProps,
})

export { message, dialog }

export type ToastType = 'info' | 'success' | 'warning' | 'error'

export function toast(text: string, type: ToastType = 'info') {
  switch (type) {
    case 'success':
      message.success(text)
      break
    case 'warning':
      message.warning(text)
      break
    case 'error':
      message.error(text)
      break
    default:
      message.info(text)
  }
}

export function confirm(title: string, content: string): Promise<boolean> {
  return new Promise((resolve) => {
    let settled = false
    const done = (value: boolean) => {
      if (!settled) {
        settled = true
        resolve(value)
      }
    }
    dialog.warning({
      title,
      content,
      positiveText: t('common.confirm'),
      negativeText: t('common.cancel'),
      onPositiveClick: () => done(true),
      onNegativeClick: () => done(false),
      onClose: () => done(false),
      onMaskClick: () => done(false),
    })
  })
}
