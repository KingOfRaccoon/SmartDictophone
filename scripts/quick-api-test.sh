#!/bin/bash

# Smart Dictophone API Quick Tests
# Simple test script to verify API is working

BASE_URL="http://localhost:8888"
KEYCLOAK_URL="http://localhost:8090"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo ""
echo "╔═══════════════════════════════════════════════════════╗"
echo "║  🧪 Smart Dictophone API Tests                       ║"
echo "╚═══════════════════════════════════════════════════════╝"
echo ""

# Test 1: Health Check
echo -e "${BLUE}Test 1: Health Check${NC}"
response=$(curl -s -o /tmp/health.txt -w "%{http_code}" $BASE_URL/health)
if [ "$response" == "200" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - API is healthy"
    echo "   Response: $(cat /tmp/health.txt)"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 200, got $response"
fi
echo ""

# Test 2: Root Endpoint
echo -e "${BLUE}Test 2: Root Endpoint${NC}"
response=$(curl -s -o /tmp/root.txt -w "%{http_code}" $BASE_URL/)
if [ "$response" == "200" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - Root endpoint accessible"
    echo "   Response: $(cat /tmp/root.txt)"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 200, got $response"
fi
echo ""

# Test 3: Swagger UI
echo -e "${BLUE}Test 3: Swagger UI${NC}"
response=$(curl -s -o /tmp/swagger.txt -w "%{http_code}" $BASE_URL/swagger-ui)
if [ "$response" == "200" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - Swagger UI accessible"
    echo "   URL: ${BASE_URL}/swagger-ui"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 200, got $response"
fi
echo ""

# Test 4: OpenAPI Documentation
echo -e "${BLUE}Test 4: OpenAPI Documentation${NC}"
response=$(curl -s -o /tmp/openapi.txt -w "%{http_code}" $BASE_URL/swagger-ui/documentation.yaml)
if [ "$response" == "200" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - OpenAPI docs available"
    lines=$(wc -l < /tmp/openapi.txt | tr -d ' ')
    echo "   Documentation: $lines lines"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 200, got $response"
fi
echo ""

# Test 5: Protected endpoint without auth (should fail)
echo -e "${BLUE}Test 5: Protected Endpoint (no auth)${NC}"
response=$(curl -s -o /tmp/protected.txt -w "%{http_code}" $BASE_URL/recordInfo)
if [ "$response" == "401" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - Correctly rejected unauthorized request"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 401, got $response"
fi
echo ""

# Test 6: Keycloak is accessible
echo -e "${BLUE}Test 6: Keycloak Availability${NC}"
response=$(curl -s -o /tmp/keycloak.txt -w "%{http_code}" $KEYCLOAK_URL/realms/smart-dictophone)
if [ "$response" == "200" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - Keycloak realm accessible"
    echo "   URL: ${KEYCLOAK_URL}/realms/smart-dictophone"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 200, got $response"
fi
echo ""

echo "────────────────────────────────────────────────────────"
echo ""
echo -e "${YELLOW}📋 Next Steps:${NC}"
echo ""
echo "1. 🌐 Open Swagger UI to test authenticated endpoints:"
echo "   ${BASE_URL}/swagger-ui"
echo ""
echo "2. 🔑 Get a token from Keycloak:"
echo "   - Open: ${KEYCLOAK_URL}/admin"
echo "   - Login: admin / admin"
echo "   - Navigate to: Realm 'smart-dictophone' > Users"
echo "   - Use existing user or create new one"
echo ""
echo "3. 🧪 Test with Keycloak user credentials:"
echo "   Default user from realm:"
echo "   - Email: user@example.com"
echo "   - Password: user123"
echo ""
echo "4. 📝 Use REST Client extension in VS Code:"
echo "   - Install: humao.rest-client"
echo "   - Open: api-tests.http"
echo "   - Click 'Send Request' above each test"
echo ""

# Check if we can get token from Keycloak
echo -e "${BLUE}Attempting to get access token...${NC}"
TOKEN_RESPONSE=$(curl -s -X POST \
  "${KEYCLOAK_URL}/realms/smart-dictophone/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=smart-dictophone-frontend" \
  -d "grant_type=password" \
  -d "username=user@example.com" \
  -d "password=user123" \
  2>/dev/null)

if echo "$TOKEN_RESPONSE" | grep -q "access_token"; then
    ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)
    echo -e "${GREEN}✓ Got access token!${NC}"
    echo ""
    
    # Test authenticated request
    echo -e "${BLUE}Test 7: Get User Info (authenticated)${NC}"
    response=$(curl -s -o /tmp/userinfo.txt -w "%{http_code}" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      $BASE_URL/recordInfo)
    
    if [ "$response" == "200" ]; then
        echo -e "${GREEN}✓ PASSED${NC} - Successfully retrieved user info"
        echo "   User data: $(cat /tmp/userinfo.txt)"
    else
        echo -e "${RED}✗ FAILED${NC} - Expected 200, got $response"
        echo "   Response: $(cat /tmp/userinfo.txt)"
    fi
    echo ""
    
    # Test get folders
    echo -e "${BLUE}Test 8: Get Folders (authenticated)${NC}"
    response=$(curl -s -o /tmp/folders.txt -w "%{http_code}" \
      -H "Authorization: Bearer $ACCESS_TOKEN" \
      $BASE_URL/folders)
    
    if [ "$response" == "200" ]; then
        echo -e "${GREEN}✓ PASSED${NC} - Successfully retrieved folders"
        echo "   Folders: $(cat /tmp/folders.txt | head -c 200)..."
    else
        echo -e "${RED}✗ FAILED${NC} - Expected 200, got $response"
    fi
    echo ""
    
    # Save token for manual testing
    echo "$ACCESS_TOKEN" > /tmp/api_token.txt
    echo -e "${YELLOW}💡 Access token saved to: /tmp/api_token.txt${NC}"
    echo "   Use it in your requests:"
    echo "   curl -H \"Authorization: Bearer \$(cat /tmp/api_token.txt)\" $BASE_URL/recordInfo"
else
    echo -e "${YELLOW}⚠ Could not get token automatically${NC}"
    echo "   You need to create a user in Keycloak first"
    echo "   Or use existing credentials"
fi

echo ""
echo "╔═══════════════════════════════════════════════════════╗"
echo "║  ✅ Tests Complete!                                   ║"
echo "╚═══════════════════════════════════════════════════════╝"
echo ""

# Cleanup
rm -f /tmp/health.txt /tmp/root.txt /tmp/swagger.txt /tmp/openapi.txt /tmp/protected.txt /tmp/keycloak.txt /tmp/userinfo.txt /tmp/folders.txt
