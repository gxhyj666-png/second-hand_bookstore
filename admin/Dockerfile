# 管理后台：多阶段构建（Node 构建静态资源 → Nginx 托管）
FROM node:20-alpine AS build
WORKDIR /app
RUN corepack enable

# 先只拷贝依赖清单，利用镜像层缓存
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
