#!/bin/bash

# Quick Start Script для Smart Dictophone
set -e

echo "🚀 Smart Dictophone - Quick Start"
echo "=================================="
echo ""

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo "❌ Docker не запущен. Запустите Docker Desktop и попробуйте снова."
    exit 1
fi

echo "📋 Stopping existing containers..."
docker-compose down -v --remove-orphans 2>/dev/null || true

echo ""
echo "🔧 Building images..."
docker-compose build --no-cache

echo ""
echo "🚀 Starting all services..."
docker-compose up -d

echo ""
echo "⏳ Waiting for services to start..."
sleep 30

echo ""
echo "🏥 Running health check..."
./scripts/health-check.sh

echo ""
echo "✅ Проект запущен успешно!"
echo ""
echo "📱 Доступные сервисы:"
echo "  • Backend API:         http://localhost:8888"
echo "  • Swagger UI:          http://localhost:8888/swagger-ui"
echo "  • Health Check:        http://localhost:8888/health"
echo "  • Keycloak Admin:      http://localhost:8090"
echo "  • MinIO Console:       http://localhost:9001"
echo "  • RabbitMQ Management: http://localhost:15672"
echo ""
echo "🔑 Учетные данные:"
echo "  • Keycloak: admin / admin"
echo "  • MinIO:    minioadmin / minioadmin"  
echo "  • RabbitMQ: rmuser / rmpassword"
echo ""
echo "🎯 Готово к использованию!"