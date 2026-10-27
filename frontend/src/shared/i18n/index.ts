import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import enUS from './locales/en-US.json'
import zhCN from './locales/zh-CN.json'

export const SUPPORTED_LOCALES = ['zh-CN', 'en-US'] as const
export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number]

const STORAGE_KEY = 'genisource.erp.locale'

function detectLocale(): SupportedLocale {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'zh-CN' || stored === 'en-US') {
    return stored
  }
  return navigator.language.toLowerCase().startsWith('zh') ? 'zh-CN' : 'en-US'
}

void i18n.use(initReactI18next).init({
  resources: {
    'zh-CN': { translation: zhCN },
    'en-US': { translation: enUS },
  },
  lng: detectLocale(),
  fallbackLng: 'zh-CN',
  interpolation: { escapeValue: false },
  returnNull: false,
})

export function changeLocale(locale: SupportedLocale): void {
  localStorage.setItem(STORAGE_KEY, locale)
  void i18n.changeLanguage(locale)
}

export function currentLocale(): SupportedLocale {
  return i18n.language === 'en-US' ? 'en-US' : 'zh-CN'
}

export default i18n
