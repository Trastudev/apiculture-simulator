FROM node:20-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev
COPY src ./src
COPY sql ./sql
COPY data ./data
ENV HOST=0.0.0.0
ENV PORT=8080
EXPOSE 8080
CMD ["node", "src/index.js"]
