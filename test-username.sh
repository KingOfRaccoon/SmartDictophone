#!/bin/bash

echo "=== Testing username preservation feature ==="
echo

# Register new user with username "testuser2026"
echo "1. Registering user with username 'testuser2026' and email 'testuser2026@test.com'..."
REGISTER_RESPONSE=$(curl -s -X POST http://localhost:8888/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser2026",
    "email": "testuser2026@test.com",
    "password": "password123",
    "firstName": "Test",
    "lastName": "User"
  }')

echo "$REGISTER_RESPONSE" | jq .
echo

# Extract access token from registration response
ACCESS_TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.accessToken')

if [ "$ACCESS_TOKEN" = "null" ] || [ -z "$ACCESS_TOKEN" ]; then
    echo "ERROR: Failed to get access token from registration"
    exit 1
fi

echo "2. Got access token from registration"
echo

# Wait a moment
sleep 2

# Get user info using the token from registration
echo "3. Getting user info with token from registration..."
USER_INFO=$(curl -s http://localhost:8888/recordInfo \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo "$USER_INFO" | jq .
echo

# Check if username field exists and has correct value
USERNAME=$(echo "$USER_INFO" | jq -r '.username')

if [ "$USERNAME" = "testuser2026" ]; then
    echo "✅ SUCCESS: Username preserved correctly as 'testuser2026'"
else
    echo "❌ FAILED: Expected username 'testuser2026', got '$USERNAME'"
    exit 1
fi

echo

# Test login with email
echo "4. Testing login with email field (not username)..."
LOGIN_RESPONSE=$(curl -s -X POST http://localhost:8888/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser2026@test.com",
    "password": "password123"
  }')

echo "$LOGIN_RESPONSE" | jq .
echo

# Extract new access token
ACCESS_TOKEN_2=$(echo "$LOGIN_RESPONSE" | jq -r '.accessToken')

if [ "$ACCESS_TOKEN_2" = "null" ] || [ -z "$ACCESS_TOKEN_2" ]; then
    echo "ERROR: Failed to login with email"
    exit 1
fi

echo "5. Got access token from login"
echo

# Wait a moment
sleep 2

# Get user info again with new token
echo "6. Getting user info with token from login..."
USER_INFO_2=$(curl -s http://localhost:8888/recordInfo \
  -H "Authorization: Bearer $ACCESS_TOKEN_2")

echo "$USER_INFO_2" | jq .
echo

# Check username again
USERNAME_2=$(echo "$USER_INFO_2" | jq -r '.username')

if [ "$USERNAME_2" = "testuser2026" ]; then
    echo "✅ SUCCESS: Username still preserved as 'testuser2026' after login"
else
    echo "❌ FAILED: Expected username 'testuser2026', got '$USERNAME_2'"
    exit 1
fi

echo
echo "=== All tests passed! ==="
echo "✅ Username is preserved correctly"
echo "✅ Login uses 'email' field (not 'username')"
echo "✅ UserInfo returns original username"
