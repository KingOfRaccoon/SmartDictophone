#!/bin/bash

# Быстрая проверка работоспособности системы

set +e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo ""
echo "🔍 Quick System Health Check"
echo "======================================"

# 1. PostgreSQL
echo -n "📊 PostgreSQL... "
if docker exec smart-dictophone-db pg_isready -U user > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
fi

# 2. RabbitMQ
echo -n "🐰 RabbitMQ... "
if curl -s -f http://localhost:15672 > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
fi

# 3. Keycloak
echo -n "🔐 Keycloak... "
if curl -s -f http://localhost:8090 > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${YELLOW}⏳ starting...${NC}"
fi

# 4. MinIO
echo -n "📦 MinIO... "
if curl -s -f http://localhost:9000/minio/health/live > /dev/null 2>&1; then
    echo -e "${GREEN}✓${NC}"
else
    echo -e "${RED}✗${NC}"
fi

# 5. API
echo -n "🚀 API... "
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health 2>/dev/null)
if [ "$HEALTH" = "200" ]; then
    echo -e "${GREEN}✓${NC}"
elif [ -z "$HEALTH" ]; then
    echo -e "${YELLOW}⏳ starting...${NC}"
else
    echo -e "${RED}✗ (HTTP $HEALTH)${NC}"
fi

echo ""
echo "======================================"

# Быстрый функциональный тест
if [ "$HEALTH" = "200" ]; then
    echo ""
    echo "🧪 Quick Functional Test"
    echo "======================================"
    
    # Попытка получить токен
    echo -n "🔑 Getting auth token... "
    TOKEN=$(curl -s -X POST "http://localhost:8090/realms/smart-dictophone/protocol/openid-connect/token" \
        -d "username=user@example.com" \
        -d "password=user123" \
        -d "grant_type=password" \
        -d "client_id=smart-dictophone-frontend" 2>/dev/null | jq -r '.access_token // empty')
    
    if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
        echo -e "${GREEN}✓${NC}"
        
        # Тест API с токеном
        echo -n "📁 Testing API with auth... "
        API_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" \
            -H "Authorization: Bearer $TOKEN" \
            http://localhost:8080/folders 2>/dev/null)
        
        if [ "$API_RESPONSE" = "200" ]; then
            echo -e "${GREEN}✓${NC}"
            echo ""
            echo -e "${GREEN}✅ System is fully operational!${NC}"
        else
            echo -e "${RED}✗ (HTTP $API_RESPONSE)${NC}"
        fi
    else
        echo -e "${YELLOW}⏳ Keycloak not configured yet${NC}"
        echo ""
        echo -e "${YELLOW}ℹ Run full E2E tests to configure Keycloak:${NC}"
        echo "  ./scripts/test-e2e.sh"
    fi
    
    echo "======================================"
fi

echo ""
