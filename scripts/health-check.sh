#!/bin/bash

# Health Check Script for Smart Dictophone
# Проверяет работоспособность всех сервисов

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
POSTGRES_PORT=5432
KEYCLOAK_PORT=8090
MINIO_PORT=9000
MINIO_CONSOLE_PORT=9001
RABBITMQ_PORT=5672
RABBITMQ_MGMT_PORT=15672
API_PORT=8888

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  Smart Dictophone Health Check${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# Function to check if port is open
check_port() {
    local host=$1
    local port=$2
    local service=$3
    
    if nc -z -w5 "$host" "$port" 2>/dev/null; then
        echo -e "${GREEN}✓${NC} $service (port $port) - ${GREEN}ONLINE${NC}"
        return 0
    else
        echo -e "${RED}✗${NC} $service (port $port) - ${RED}OFFLINE${NC}"
        return 1
    fi
}

# Function to check HTTP endpoint
check_http() {
    local url=$1
    local service=$2
    local expected_status=${3:-200}
    
    status_code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo "000")
    
    if [ "$status_code" -eq "$expected_status" ]; then
        echo -e "${GREEN}✓${NC} $service - ${GREEN}HEALTHY${NC} (HTTP $status_code)"
        return 0
    else
        echo -e "${RED}✗${NC} $service - ${RED}UNHEALTHY${NC} (HTTP $status_code)"
        return 1
    fi
}

# Function to check Docker container
check_container() {
    local container_name=$1
    local service=$2
    
    if docker ps --format '{{.Names}}' | grep -q "^${container_name}$"; then
        status=$(docker inspect --format='{{.State.Status}}' "$container_name" 2>/dev/null)
        health=$(docker inspect --format='{{.State.Health.Status}}' "$container_name" 2>/dev/null || echo "none")
        
        if [ "$status" = "running" ]; then
            if [ "$health" = "healthy" ] || [ "$health" = "none" ]; then
                echo -e "${GREEN}✓${NC} $service container - ${GREEN}RUNNING${NC}"
                if [ "$health" != "none" ]; then
                    echo -e "  └─ Health status: ${GREEN}$health${NC}"
                fi
                return 0
            else
                echo -e "${YELLOW}⚠${NC} $service container - ${YELLOW}RUNNING (health: $health)${NC}"
                # Don't count this as failure, just a warning
                return 0
            fi
        else
            echo -e "${RED}✗${NC} $service container - ${RED}$status${NC}"
            return 1
        fi
    else
        echo -e "${RED}✗${NC} $service container - ${RED}NOT FOUND${NC}"
        return 1
    fi
}

# Counter for failed checks
FAILED=0

echo -e "${BLUE}1. Checking Docker Containers...${NC}"
echo ""

check_container "smart_dictophone-postgres-1" "PostgreSQL" || ((FAILED++))
check_container "smart_dictophone-keycloak-1" "Keycloak" || ((FAILED++))
check_container "smart_dictophone-minio-1" "MinIO" || ((FAILED++))
check_container "smart_dictophone-rabbitmq-1" "RabbitMQ" || ((FAILED++))
check_container "smart_dictophone-api-1" "Backend API" || ((FAILED++))

echo ""
echo -e "${BLUE}2. Checking Network Ports...${NC}"
echo ""

check_port "localhost" $POSTGRES_PORT "PostgreSQL" || ((FAILED++))
check_port "localhost" $KEYCLOAK_PORT "Keycloak" || ((FAILED++))
check_port "localhost" $MINIO_PORT "MinIO S3" || ((FAILED++))
check_port "localhost" $MINIO_CONSOLE_PORT "MinIO Console" || ((FAILED++))
check_port "localhost" $RABBITMQ_PORT "RabbitMQ" || ((FAILED++))
check_port "localhost" $RABBITMQ_MGMT_PORT "RabbitMQ Management" || ((FAILED++))
check_port "localhost" $API_PORT "Backend API" || ((FAILED++))

echo ""
echo -e "${BLUE}3. Checking HTTP Endpoints...${NC}"
echo ""

# Keycloak realm
check_http "http://localhost:8090/realms/smart-dictophone" "Keycloak Realm" || ((FAILED++))

# MinIO health
check_http "http://localhost:9000/minio/health/live" "MinIO Health" || ((FAILED++))

# RabbitMQ Management UI
check_http "http://localhost:15672" "RabbitMQ Management UI" || ((FAILED++))

# Backend API
check_http "http://localhost:8888" "Backend API Root" || ((FAILED++))

# Check for health endpoint (optional)
if check_http "http://localhost:8888/health" "Health Endpoint" 200 2>/dev/null; then
    :  # Success, do nothing
else
    echo -e "${YELLOW}⚠${NC} Health Endpoint - ${YELLOW}NOT CONFIGURED${NC}"
fi

echo ""
echo -e "${BLUE}4. Checking Service Connectivity...${NC}"
echo ""

# Check PostgreSQL from API logs (connection happens at startup)
if docker logs smart_dictophone-api-1 2>&1 | grep -q "Exposed -"; then
    echo -e "${GREEN}✓${NC} API → PostgreSQL - ${GREEN}CONNECTED${NC}"
else
    echo -e "${RED}✗${NC} API → PostgreSQL - ${RED}DISCONNECTED${NC}"
    ((FAILED++))
fi

# Check Keycloak from API container
if docker exec smart_dictophone-api-1 sh -c "curl -sf http://keycloak:8080/realms/smart-dictophone > /dev/null" 2>/dev/null; then
    echo -e "${GREEN}✓${NC} API → Keycloak - ${GREEN}CONNECTED${NC}"
else
    echo -e "${RED}✗${NC} API → Keycloak - ${RED}DISCONNECTED${NC}"
    ((FAILED++))
fi

# Check MinIO from API container
if docker exec smart_dictophone-api-1 sh -c "curl -sf http://minio:9000/minio/health/live > /dev/null" 2>/dev/null; then
    echo -e "${GREEN}✓${NC} API → MinIO - ${GREEN}CONNECTED${NC}"
else
    echo -e "${RED}✗${NC} API → MinIO - ${RED}DISCONNECTED${NC}"
    ((FAILED++))
fi

# Check RabbitMQ from API logs (connection happens at startup)
if docker logs smart_dictophone-api-1 2>&1 | grep -q "Connected to RabbitMQ"; then
    echo -e "${GREEN}✓${NC} API → RabbitMQ - ${GREEN}CONNECTED${NC}"
else
    echo -e "${RED}✗${NC} API → RabbitMQ - ${RED}DISCONNECTED${NC}"
    ((FAILED++))
fi

echo ""
echo -e "${BLUE}5. Checking RabbitMQ Queue...${NC}"
echo ""

# Check if transcription queue exists
QUEUE_INFO=$(docker exec smart_dictophone-rabbitmq-1 rabbitmqctl list_queues name 2>/dev/null | grep "audio-transcription" || echo "")

if [ -n "$QUEUE_INFO" ]; then
    echo -e "${GREEN}✓${NC} RabbitMQ Queue 'audio-transcription' - ${GREEN}EXISTS${NC}"
else
    echo -e "${YELLOW}⚠${NC} RabbitMQ Queue 'audio-transcription' - ${YELLOW}NOT FOUND${NC}"
    echo -e "  ${YELLOW}Note: Queue will be created on first use${NC}"
fi

echo ""
echo -e "${BLUE}6. Database Tables Check...${NC}"
echo ""

# Check if database tables exist
TABLES=$(docker exec smart_dictophone-postgres-1 psql -U postgres -d smart_dictophone -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public';" 2>/dev/null | tr -d ' ')

if [ "$TABLES" -gt 0 ]; then
    echo -e "${GREEN}✓${NC} Database tables - ${GREEN}$TABLES tables found${NC}"
    
    # List main tables
    echo -e "  ${BLUE}Main tables:${NC}"
    docker exec smart_dictophone-postgres-1 psql -U postgres -d smart_dictophone -t -c "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;" 2>/dev/null | while read -r table; do
        if [ -n "$table" ]; then
            echo -e "    - ${table}"
        fi
    done
else
    echo -e "${YELLOW}⚠${NC} Database tables - ${YELLOW}EMPTY (may need migration)${NC}"
fi

echo ""
echo -e "${BLUE}7. MinIO Bucket Check...${NC}"
echo ""

# Check if MinIO bucket exists
if docker exec smart_dictophone-minio-1 sh -c "mc alias set local http://localhost:9000 minioadmin minioadmin > /dev/null 2>&1 && mc ls local/smart-dictophone-audio > /dev/null 2>&1" 2>/dev/null; then
    echo -e "${GREEN}✓${NC} MinIO bucket 'smart-dictophone-audio' - ${GREEN}EXISTS${NC}"
else
    echo -e "${RED}✗${NC} MinIO bucket 'smart-dictophone-audio' - ${RED}NOT FOUND${NC}"
    ((FAILED++))
fi

echo ""
echo -e "${BLUE}================================================${NC}"

# Summary
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All checks passed! System is healthy.${NC}"
    echo ""
    echo -e "${BLUE}Quick Links:${NC}"
    echo -e "  • Backend API:         ${BLUE}http://localhost:8888${NC}"
    echo -e "  • Health Check:        ${BLUE}http://localhost:8888/health${NC}"
    echo -e "  • Keycloak Admin:      ${BLUE}http://localhost:8090${NC}"
    echo -e "  • MinIO Console:       ${BLUE}http://localhost:9001${NC}"
    echo -e "  • RabbitMQ Management: ${BLUE}http://localhost:15672${NC}"
    echo ""
    echo -e "${BLUE}Default Credentials:${NC}"
    echo -e "  • Keycloak: admin / admin"
    echo -e "  • MinIO:    minioadmin / minioadmin"
    echo -e "  • RabbitMQ: rmuser / rmpassword"
    echo ""
    exit 0
else
    echo -e "${RED}✗ $FAILED check(s) failed!${NC}"
    echo ""
    echo -e "${YELLOW}Troubleshooting:${NC}"
    echo -e "  1. Check logs: ${BLUE}docker-compose logs -f${NC}"
    echo -e "  2. Restart services: ${BLUE}docker-compose restart${NC}"
    echo -e "  3. Full restart: ${BLUE}docker-compose down && docker-compose up -d${NC}"
    echo ""
    exit 1
fi
