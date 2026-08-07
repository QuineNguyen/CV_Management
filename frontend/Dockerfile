FROM nginx:alpine
RUN apk add --no-cache tzdata
ENV TZ=Asia/Ho_Chi_Minh
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY index.html /usr/share/nginx/html/index.html
COPY src/app/ /usr/share/nginx/html/
EXPOSE 8000
