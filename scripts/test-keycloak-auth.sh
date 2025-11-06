#!/bin/bash

# Скрипт для тестирования аутентификации в Keycloak
# Использование: ./scripts/test-keycloak-auth.sh [username] [password]

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8090}"
REALM="${KEYCLOAK_REALM:-smart-dictophone}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID:-smart-dictophone-backend}"
CLIENT_SECRET="${KEYCLOAK_CLIENT_SECRET:-your-backend-client-secret-change-me}"

USERNAME="${1:-user@example.com}"
PASSWORD="${2:-user123}"

echo "🔐 Тестирование аутентификации в Keycloak"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Keycloak URL: ${KEYCLOAK_URL}"
echo "Realm:        ${REALM}"
echo "Client ID:    ${CLIENT_ID}"
echo "Username:     ${USERNAME}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 1. Проверка доступности Keycloak
echo "1️⃣  Проверка доступности Keycloak..."
if curl -sf "${KEYCLOAK_URL}/realms/${REALM}" > /dev/null; then
  echo "✅ Keycloak доступен"
else
  echo "❌ Keycloak недоступен по адресу ${KEYCLOAK_URL}"
  exit 1
fi
echo ""

# 2. Получение токена
echo "2️⃣  Получение access token..."
TOKEN_RESPONSE=$(curl -s -X POST "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "grant_type=password" \
  -d "username=${USERNAME}" \
  -d "password=${PASSWORD}")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token')
REFRESH_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.refresh_token')
EXPIRES_IN=$(echo "$TOKEN_RESPONSE" | jq -r '.expires_in')

if [ -z "$ACCESS_TOKEN" ] || [ "$ACCESS_TOKEN" == "null" ]; then
  echo "❌ Ошибка получения токена:"
  echo "$TOKEN_RESPONSE" | jq .
  exit 1
fi

echo "✅ Токен получен успешно"
echo "   Expires in: ${EXPIRES_IN} секунд"
echo ""

# 3. Декодирование токена
echo "3️⃣  Декодирование access token..."
TOKEN_PAYLOAD=$(echo "$ACCESS_TOKEN" | cut -d '.' -f 2 | base64 -d 2>/dev/null)

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📋 Информация из токена:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "$TOKEN_PAYLOAD" | jq '{
  sub: .sub,
  email: .email,
  name: .name,
  preferred_username: .preferred_username,
  realm_roles: .realm_access.roles,
  exp: .exp,
  iat: .iat
}'
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 4. Получение информации о пользователе
echo "4️⃣  Получение информации о пользователе..."
USERINFO_RESPONSE=$(curl -s -X GET "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/userinfo" \
  -H "Authorization: Bearer ${ACCESS_TOKEN}")

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "👤 UserInfo endpoint:"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "$USERINFO_RESPONSE" | jq .
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 5. Проверка валидности токена
echo "5️⃣  Проверка валидности токена (introspection)..."
INTROSPECT_RESPONSE=$(curl -s -X POST "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token/introspect" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -u "${CLIENT_ID}:${CLIENT_SECRET}" \
  -d "token=${ACCESS_TOKEN}")

IS_ACTIVE=$(echo "$INTROSPECT_RESPONSE" | jq -r '.active')

if [ "$IS_ACTIVE" == "true" ]; then
  echo "✅ Токен валиден"
else
  echo "❌ Токен невалиден"
fi
echo ""

# 6. Тест refresh token
echo "6️⃣  Тестирование refresh token..."
REFRESH_RESPONSE=$(curl -s -X POST "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=${CLIENT_ID}" \
  -d "client_secret=${CLIENT_SECRET}" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=${REFRESH_TOKEN}")

NEW_ACCESS_TOKEN=$(echo "$REFRESH_RESPONSE" | jq -r '.access_token')

if [ -z "$NEW_ACCESS_TOKEN" ] || [ "$NEW_ACCESS_TOKEN" == "null" ]; then
  echo "❌ Ошибка обновления токена"
else
  echo "✅ Токен успешно обновлен"
fi
echo ""

# 7. Сохранение токенов
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🔑 Токены (сохранены в переменных окружения):"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "export ACCESS_TOKEN='${ACCESS_TOKEN}'"
echo "export REFRESH_TOKEN='${REFRESH_TOKEN}'"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# 8. Примеры использования
echo "💡 Примеры использования токена:"
echo ""
echo "# Запрос к вашему API:"
echo "curl -X GET http://localhost:8080/api/folders \\"
echo "  -H 'Authorization: Bearer ${ACCESS_TOKEN}'"
echo ""
echo "# Получить информацию о пользователе:"
echo "curl -X GET ${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/userinfo \\"
echo "  -H 'Authorization: Bearer ${ACCESS_TOKEN}'"
echo ""
echo "# Logout:"
echo "curl -X POST ${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/logout \\"
echo "  -H 'Content-Type: application/x-www-form-urlencoded' \\"
echo "  -d 'client_id=${CLIENT_ID}' \\"
echo "  -d 'client_secret=${CLIENT_SECRET}' \\"
echo "  -d 'refresh_token=${REFRESH_TOKEN}'"
echo ""

echo "🎉 Тестирование завершено успешно!"
