#!/usr/bin/env bash
#
# Copyright © ${year} the original author or authors (piergiorgio@apache.org)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Dedicated Integration Test Script for Camunda Platform 7 Repository Connector
set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}=== Starting OpenCrawling Camunda Connector Integration Test ===${NC}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

command -v docker >/dev/null 2>&1 || { echo -e "${RED}Docker is required but not installed. Aborting.${NC}" >&2; exit 1; }

CONTAINER_NAME="opencrawling-camunda-7-test"
CAMUNDA_PORT=8089

cleanup() {
  echo -e "${YELLOW}Cleaning up Camunda test container...${NC}"
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

cleanup

echo -e "${YELLOW}Starting Camunda Platform 7 container (${CONTAINER_NAME})...${NC}"
docker run -d --name "$CONTAINER_NAME" \
  -p ${CAMUNDA_PORT}:8080 \
  camunda/camunda-bpm-platform:7.23.0

echo -e "${YELLOW}Waiting for Camunda REST service at http://localhost:${CAMUNDA_PORT}/engine-rest ...${NC}"

TIMEOUT=120
ELAPSED=0
HEALTHY=false

until [ "$HEALTHY" = true ]; do
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -u demo:demo "http://localhost:${CAMUNDA_PORT}/engine-rest/history/process-instance?maxResults=1" || true)
  if [ "$HTTP_CODE" = "200" ]; then
    HEALTHY=true
    echo -e "${GREEN}Camunda REST engine is ready! (HTTP 200)${NC}"
    break
  fi
  if [ $ELAPSED -ge $TIMEOUT ]; then
    echo -e "${RED}Timed out waiting for Camunda REST engine. HTTP Code: $HTTP_CODE${NC}"
    docker logs "$CONTAINER_NAME" --tail 30 || true
    exit 1
  fi
  sleep 3
  ELAPSED=$((ELAPSED + 3))
  echo -n "."
done

echo -e "${YELLOW}Running Maven tests for oc-camunda-repository-connector...${NC}"
mvn clean test -pl oc-camunda-repository-connector -Dspring.opencrawling.connector.camunda.url="http://localhost:${CAMUNDA_PORT}/engine-rest" -Dspring.opencrawling.connector.camunda.username="demo" -Dspring.opencrawling.connector.camunda.password="demo"

echo -e "${GREEN}=== Camunda Repository Connector Integration Test PASSED Successfully ===${NC}"
