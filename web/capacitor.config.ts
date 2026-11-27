/**
 * Capacitor 配置（预留）：本目录现在产出的是纯 Web 站点，
 * 需要打包 APK 时再执行：
 *   pnpm add -D @capacitor/cli && pnpm add @capacitor/core @capacitor/android
 *   npx cap init 同窗书 com.textbook.loop --web-dir=dist
 *   npx cap add android && pnpm build && npx cap sync android
 * 然后用 Android Studio 打开 android/ 目录出包。
 *
 * 注意：打包 APK 后页面不再有 /api 反向代理，必须把 web/.env.production 的
 * VITE_API_BASE 指到公网后端地址（例如 https://api.example.com）。
 */
export default {
  appId: 'com.textbook.loop',
  appName: '同窗书',
  webDir: 'dist',
  bundledWebRuntime: false,
  server: {
    androidScheme: 'https'
  }
}
