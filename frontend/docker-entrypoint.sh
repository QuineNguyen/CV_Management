#!/bin/sh
cat <<EOF > /usr/share/nginx/html/env.js
window.__env = {
  GOOGLE_CLIENT_ID: "${GOOGLE_CLIENT_ID:-}"
};
EOF
exec nginx -g 'daemon off;'
