# build environment
FROM node:22-alpine as build
WORKDIR /app
COPY package*.json ./
# TODO: replace with `npm ci` once package-lock.json files are committed (TD-08)
RUN npm install
COPY . .
RUN npm run build

# production environment
FROM nginx:stable-alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
