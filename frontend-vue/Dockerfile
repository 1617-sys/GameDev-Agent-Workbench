FROM node:22-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM node:22-alpine
WORKDIR /app

RUN npm install -g serve@14.2.4 \
    && addgroup -S app \
    && adduser -S -G app app
COPY --from=build --chown=app:app /app/dist ./dist

EXPOSE 5173
USER app
CMD ["serve", "-s", "dist", "-l", "5173"]
