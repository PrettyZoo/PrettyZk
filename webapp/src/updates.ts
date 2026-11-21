import { api, openExternal } from './api.ts'
import { dialog, toast } from './composables/feedback.ts'
import { t } from './i18n.ts'

/**
 * Check the GitHub release/tag feed. Silent checks are used on startup; manual
 * checks are triggered from the status bar and report "up to date" / errors.
 *
 * The prompt opens the release page. In-app download/install needs the Tauri
 * updater plugin plus signed update artifacts and release metadata.
 */
export async function checkForUpdates(options: { silent: boolean }): Promise<void> {
  try {
    const result = await api.checkLatestVersion()
    if (!result.updateAvailable) {
      if (!options.silent) {
        toast(t('status.upToDate'), 'success')
      }
      return
    }
    dialog.warning({
      title: t('status.checkUpdate'),
      content: t('status.updateAvailable', { version: result.latest }),
      positiveText: t('status.updateDownload'),
      negativeText: t('status.updateLater'),
      onPositiveClick: async () => {
        if (!result.url) return
        try {
          await openExternal(result.url)
        } catch (e: any) {
          toast(t('status.updateFailed', { msg: e.message }), 'error')
        }
      },
    })
  } catch (e: any) {
    if (!options.silent) {
      toast(t('status.updateFailed', { msg: e.message }), 'error')
    }
  }
}
