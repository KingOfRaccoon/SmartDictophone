#!/bin/bash

# Smart Dictophone API Tests
# Run all API tests and save results

BASE_URL="http://localhost:8888"
KEYCLOAK_URL="http://localhost:8090"
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "======================================"
echo "  Smart Dictophone API Tests"
echo "======================================"
echo ""

# Test 1: Health Check
echo -e "${YELLOW}Test 1: Health Check${NC}"
response=$(curl -s -o /tmp/response.txt -w "%{http_code}" $BASE_URL/health)
http_code=$response
body=$(cat /tmp/response.txt)

if [ "$http_code" == "200" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - Health check successful"
    echo "Response: $body"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 200, got $http_code"
fi
echo ""

# Test 2: Register New User
echo -e "${YELLOW}Test 2: Register New User${NC}"
timestamp=$(date +%s)
username="testuser_$timestamp"
email="${username}@example.com"

response=$(curl -s -w "\n%{http_code}" -X POST $BASE_URL/register \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"$username\",
    \"email\": \"$email\",
    \"password\": \"test123456\",
    \"firstName\": \"Test\",
    \"lastName\": \"User\"
  }")

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" == "201" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - User registered successfully"
    accessToken=$(echo "$body" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
    refreshToken=$(echo "$body" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)
    userId=$(echo "$body" | grep -o '"userId":"[^"]*' | cut -d'"' -f4)
    echo "User ID: $userId"
    echo "Access Token: ${accessToken:0:50}..."
else
    echo -e "${RED}✗ FAILED${NC} - Expected 201, got $http_code"
    echo "Response: $body"
fi
echo ""

# Test 3: Login
echo -e "${YELLOW}Test 3: Login with new user${NC}"
response=$(curl -s -w "\n%{http_code}" -X POST $BASE_URL/login \
  -H "Content-Type: application/json" \
  -d "{
    \"email\": \"$email\",
    \"password\": \"test123456\"
  }")

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" == "200" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - Login successful"
    accessToken=$(echo "$body" | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
    refreshToken=$(echo "$body" | grep -o '"refreshToken":"[^"]*' | cut -d'"' -f4)
else
    echo -e "${RED}✗ FAILED${NC} - Expected 200, got $http_code"
    echo "Response: $body"
fi
echo ""

# Test 4: Login On Token (validate token)
echo -e "${YELLOW}Test 4: Validate Token${NC}"
response=$(curl -s -w "\n%{http_code}" -X POST $BASE_URL/loginOnToken \
  -H "Authorization: Bearer $accessToken")

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" == "200" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - Token is valid"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 200, got $http_code"
    echo "Response: $body"
fi
echo ""

# Test 5: Get User Info
echo -e "${YELLOW}Test 5: Get User Info${NC}"
response=$(curl -s -w "\n%{http_code}" -X GET $BASE_URL/recordInfo \
  -H "Authorization: Bearer $accessToken")

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" == "200" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - User info retrieved"
    echo "Response: $body"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 200, got $http_code"
    echo "Response: $body"
fi
echo ""

# Test 6: Create Folder
echo -e "${YELLOW}Test 6: Create Folder${NC}"
response=$(curl -s -w "\n%{http_code}" -X POST $BASE_URL/folders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $accessToken" \
  -d '{
    "name": "Test Folder",
    "description": "Test folder for API tests"
  }')

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" == "201" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - Folder created"
    folderId=$(echo "$body" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
    echo "Folder ID: $folderId"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 201, got $http_code"
    echo "Response: $body"
fi
echo ""

# Test 7: Get All Folders
echo -e "${YELLOW}Test 7: Get All Folders${NC}"
response=$(curl -s -w "\n%{http_code}" -X GET $BASE_URL/folders \
  -H "Authorization: Bearer $accessToken")

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" == "200" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - Folders retrieved"
    echo "Response: $body"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 200, got $http_code"
    echo "Response: $body"
fi
echo ""

# Test 8: Update Folder
if [ ! -z "$folderId" ]; then
    echo -e "${YELLOW}Test 8: Update Folder${NC}"
    response=$(curl -s -w "\n%{http_code}" -X PUT $BASE_URL/folders/$folderId \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $accessToken" \
      -d '{
        "name": "Updated Test Folder",
        "description": "Updated description"
      }')

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" == "200" ]; then
        echo -e "${GREEN}✓ PASSED${NC} - Folder updated"
    else
        echo -e "${RED}✗ FAILED${NC} - Expected 200, got $http_code"
        echo "Response: $body"
    fi
    echo ""
fi

# Test 9: Get Records
echo -e "${YELLOW}Test 9: Get Records${NC}"
response=$(curl -s -w "\n%{http_code}" -X GET "$BASE_URL/records?page=0&size=20" \
  -H "Authorization: Bearer $accessToken")

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n-1)

if [ "$http_code" == "200" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - Records retrieved"
    echo "Response: $body"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 200, got $http_code"
    echo "Response: $body"
fi
echo ""

# Test 10: Delete Folder
if [ ! -z "$folderId" ]; then
    echo -e "${YELLOW}Test 10: Delete Folder${NC}"
    response=$(curl -s -w "\n%{http_code}" -X DELETE $BASE_URL/folders/$folderId \
      -H "Authorization: Bearer $accessToken")

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | head -n-1)

    if [ "$http_code" == "204" ] || [ "$http_code" == "200" ]; then
        echo -e "${GREEN}✓ PASSED${NC} - Folder deleted"
    else
        echo -e "${RED}✗ FAILED${NC} - Expected 204/200, got $http_code"
        echo "Response: $body"
    fi
    echo ""
fi

# Error Cases
echo "======================================"
echo "  Error Cases Tests"
echo "======================================"
echo ""

# Test: Login with wrong credentials
echo -e "${YELLOW}Test: Login with wrong credentials${NC}"
response=$(curl -s -w "\n%{http_code}" -X POST $BASE_URL/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "wrong@example.com",
    "password": "wrongpassword"
  }')

http_code=$(echo "$response" | tail -n1)

if [ "$http_code" == "401" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - Correctly rejected wrong credentials"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 401, got $http_code"
fi
echo ""

# Test: Access without token
echo -e "${YELLOW}Test: Access protected route without token${NC}"
response=$(curl -s -w "\n%{http_code}" -X GET $BASE_URL/recordInfo)

http_code=$(echo "$response" | tail -n1)

if [ "$http_code" == "401" ]; then
    echo -e "${GREEN}✓ PASSED${NC} - Correctly rejected request without token"
else
    echo -e "${RED}✗ FAILED${NC} - Expected 401, got $http_code"
fi
echo ""

echo "======================================"
echo "  Tests Complete!"
echo "======================================"
