#!/bin/sh
# Container healthcheck: BusyBox wget (already in the Alpine base, so nothing extra to install)
# exits non-zero unless readiness answers 2xx. -T 2 keeps it inside HEALTHCHECK's 3 s timeout.
exec wget -q -T 2 -O /dev/null "http://127.0.0.1:${HEALTH_PORT:-8080}/actuator/health/readiness"
