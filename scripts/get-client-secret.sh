#!/bin/bash

# Скрипт для получения client secret из Keycloak
# Использование: ./scripts/get-client-secret.sh

set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8090}"
ADMIN_USERNAME="${KEYCLOAK_ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
REALM="${KEYCLOAK_REALM:-smart-dictophone}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID:-smart-dictophone-backend}"

echo "🔐 Получение client secret из Keycloak..."
echo ""

# Получить admin токен
echo "📝 Получение admin токена..."
ADMIN_TOKEN=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli" \
  -d "username=${ADMIN_USERNAME}" \
  -d "password=${ADMIN_PASSWORD}" \
  -d "grant_type=password" | jq -r '.access_token')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" == "null" ]; then
  echo "❌ Ошибка: Не удалось получить admin токен"
  echo "Проверьте, что Keycloak запущен и доступен по адресу ${KEYCLOAK_URL}"
  exit 1
fi

echo "✅ Admin токен получен"
echo ""

# Получить ID клиента
echo "🔍 Поиск клиента ${CLIENT_ID}..."
CLIENT_UUID=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/clients" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | jq -r ".[] | select(.clientId==\"${CLIENT_ID}\") | .id")

if [ -z "$CLIENT_UUID" ] || [ "$CLIENT_UUID" == "null" ]; then
  echo "❌ Ошибка: Клиент ${CLIENT_ID} не найден в realm ${REALM}"
  exit 1
fi

echo "✅ Клиент найден (UUID: ${CLIENT_UUID})"
echo ""

# Получить client secret
echo "🔑 Получение client secret..."
CLIENT_SECRET=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/clients/${CLIENT_UUID}/client-secret" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" | jq -r '.value')

if [ -z "$CLIENT_SECRET" ] || [ "$CLIENT_SECRET" == "null" ]; then
  echo "❌ Ошибка: Не удалось получить client secret"
  exit 1
fi

echo "✅ Client secret получен"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📋 Информация о клиенте"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "Realm:         ${REALM}"
echo "Client ID:     ${CLIENT_ID}"
echo "Client Secret: ${CLIENT_SECRET}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "💡 Для обновления конфигурации добавьте в application.yaml:"
echo ""
echo "keycloak:"
echo "  serverUrl: ${KEYCLOAK_URL}"
echo "  realm: ${REALM}"
echo "  clientId: ${CLIENT_ID}"
echo "  clientSecret: ${CLIENT_SECRET}"
echo ""
echo "💡 Или создайте .env файл:"
echo ""
echo "KEYCLOAK_SERVER_URL=${KEYCLOAK_URL}"
echo "KEYCLOAK_REALM=${REALM}"
echo "KEYCLOAK_CLIENT_ID=${CLIENT_ID}"
echo "KEYCLOAK_CLIENT_SECRET=${CLIENT_SECRET}"
echo ""

# Опционально сохранить в файл
read -p "💾 Сохранить в .env файл? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
  cat > .env << EOF
# Keycloak Configuration
KEYCLOAK_SERVER_URL=${KEYCLOAK_URL}
KEYCLOAK_REALM=${REALM}
KEYCLOAK_CLIENT_ID=${CLIENT_ID}
KEYCLOAK_CLIENT_SECRET=${CLIENT_SECRET}
KEYCLOAK_ADMIN_USERNAME=${ADMIN_USERNAME}
KEYCLOAK_ADMIN_PASSWORD=${ADMIN_PASSWORD}
EOF
  echo "✅ Конфигурация сохранена в .env"
fi

echo ""
echo "🎉 Готово!"
