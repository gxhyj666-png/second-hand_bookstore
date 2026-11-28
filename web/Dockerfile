# 用户端 Web（H5）：Vite 构建静态资源 → Nginx 托管，/api 反代到后端
FROM node:24-alpine AS build
WORKDIR /app
# 国内网络：npm / pnpm 都走 npmmirror，否则 registry.npmjs.org 会慢到超时
ENV NPM_CONFIG_REGISTRY=https://registry.npmmirror.com
# pnpm 11 需要 Node >= 22.13（用了 node:sqlite），所以基础镜像用 24
RUN npm install -g pnpm@11.8.0

# 先只拷贝依赖清单，利用镜像层缓存
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80