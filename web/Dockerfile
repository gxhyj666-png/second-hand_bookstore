# 用户端 Web（H5）：Vite 构建静态资源 → Nginx 托管，/api 反代到后端
FROM node:20-alpine AS build
WORKDIR /app
# 直接装固定版本 pnpm（比 corepack 少一层网络不确定性）
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
