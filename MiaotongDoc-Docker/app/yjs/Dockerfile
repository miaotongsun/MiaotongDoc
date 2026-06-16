FROM node:20-alpine

WORKDIR /app

COPY package.json ./
RUN npm install --production

COPY server.js ./

EXPOSE 1234

HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -q -O - http://localhost:1234/health || exit 1

CMD ["node", "server.js"]
