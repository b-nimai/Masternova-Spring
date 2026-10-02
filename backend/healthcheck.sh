#!/usr/bin/env bash
# Container healthcheck with no curl/wget (the JRE image ships neither, and adding them only adds
# CVEs): open a TCP socket with bash's /dev/tcp, send a raw HTTP request, expect a 200.
set -euo pipefail
exec 3<>"/dev/tcp/127.0.0.1/${HEALTH_PORT:-8080}"
printf 'GET /actuator/health/readiness HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
head -n1 <&3 | grep -q ' 200 '
