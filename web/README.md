# 同窗书 · 用户端 Web（H5）

面向同学（买家 / 卖家 / 团长）的 Web 应用：一套响应式代码，手机浏览器与 PC 浏览器都能用，将来可封装成 APK。
后端在 `../server`，管理后台在 `../admin`，原生小程序在 `../miniapp`（保留作参考，不是主交付）。

## 技术栈

Vue 3（`<script setup>`）+ Vue Router 4（hash 模式）+ Pinia + Vant 4 + axios + Vite 5。
不引入 UI 框架之外的运行时依赖，构建产物是纯静态文件。

## 本地运行

```powershell
# 1. 先起后端（8088）
cd ..\server ; mvn spring-boot:run

# 2. 再起本应用（5174）
cd ..\web
pnpm install
pnpm dev          # http://localhost:5174
```

开发期 `/api` 由 Vite 代理到 `http://localhost:8088` 并去掉 `/api` 前缀（后端路由是 `/v1/...`）。
演示账号：`buyer001 / 123456`、`seller001 / 123456`、`clerk001 / 123456`（核销）、`admin / admin123`。

## 构建与部署

```powershell
pnpm build        # 产物在 dist/，纯静态
```

- Nginx 托管：`web/nginx.conf` 已写好（`/` 静态 + `/api/` 反代到 `server:8088` + gzip）。
- Docker：`docker compose up -d web server mysql redis`，访问 `http://<IP>/`。
- 部署到子路径也支持：`vite.config.js` 里 `base: './'` 用的是相对路径。

## 为什么是这种形态

| 选择 | 原因 |
|---|---|
| 响应式 Web 一套，而不是 PC/移动两套 | 页面只有 21 个，写两套维护成本翻倍；CSS grid 断点（手机 2 列 / 平板 3 列 / 桌面 4 列）足够覆盖 |
| hash 路由 | 静态托管不需要 history fallback；将来 Capacitor 以本地文件加载也不会 404 |
| `base: './'` | 既能部署在根路径，也能部署在任意子目录 |
| Vant 全量引入 | 减少构建配置与按需加载的坑；首屏 gzip 后约 400 KB，校园场景可接受 |

## 打包 APK（后续）

```powershell
pnpm add -D @capacitor/cli
pnpm add @capacitor/core @capacitor/android
npx cap init 同窗书 com.textbook.loop --web-dir=dist
npx cap add android
pnpm build ; npx cap sync android
npx cap open android      # Android Studio 出包，需要本机装 Android SDK
```

**两个前提**：
1. APK 里没有 Nginx 反代，`web/.env.production` 必须写 `VITE_API_BASE=https://你的域名` 指向公网后端（或 `http://<ECS IP>:8088`，但 Android 9+ 默认禁止明文 HTTP，需要 `android:usesCleartextTraffic="true"`）。
2. 生产环境要 HTTPS：小程序/APK 都要求；可用阿里云 SSL 免费证书 + Nginx。

## 目录

```
web/
├── index.html
├── vite.config.js        # base './'，dev 代理 /api → 8088
├── capacitor.config.ts   # APK 预留配置（现在不影响 Web 构建）
├── nginx.conf / Dockerfile
└── src/
    ├── main.js / App.vue        # 全局 Vant + tabbar
    ├── router/index.js          # 21 个路由，hash 模式，auth 守卫
    ├── api/{request,index}.js   # axios 封装 + 全部接口
    ├── store/user.js            # 登录态（token/localStorage）
    ├── utils/format.js          # 金额、状态文案、时间
    ├── styles/base.css          # CSS 变量与响应式栅格
    ├── components/              # ListingCard / EmptyState / PriceIndex / SectionTitle
    └── views/                   # 21 个页面
```

## 已知限制

- 图片只接受 URL（后端不存文件），生产建议接 OSS。
- 支付是 mock 适配器（`orderApi.pay`），没有接真实商户号。
- 没有 i18n、没有离线缓存（未上 PWA），如果要"像 App"，可后续加 `vite-plugin-pwa`。
