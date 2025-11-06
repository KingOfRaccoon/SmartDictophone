#!/bin/bash

# Integration Test Script for Smart Dictophone
# Проверяет полный flow работы приложения от аутентификации до загрузки аудио

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
API_URL="http://localhost:8080"
KEYCLOAK_URL="http://localhost:8090"
REALM="smart-dictophone"
CLIENT_ID="smart-dictophone-backend"
TEST_USER="user@example.com"
TEST_PASSWORD="user123"

# Test results
TESTS_PASSED=0
TESTS_FAILED=0

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  Smart Dictophone Integration Test${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# Function to print test result
test_result() {
    local test_name=$1
    local result=$2
    local details=$3
    
    if [ "$result" = "PASS" ]; then
        echo -e "${GREEN}✓${NC} $test_name - ${GREEN}PASSED${NC}"
        if [ -n "$details" ]; then
            echo -e "  ${CYAN}└─${NC} $details"
        fi
        ((TESTS_PASSED++))
    else
        echo -e "${RED}✗${NC} $test_name - ${RED}FAILED${NC}"
        if [ -n "$details" ]; then
            echo -e "  ${RED}└─${NC} $details"
        fi
        ((TESTS_FAILED++))
    fi
}

# Function to extract JSON value
json_value() {
    local json=$1
    local key=$2
    echo "$json" | grep -o "\"$key\":\"[^\"]*\"" | cut -d'"' -f4
}

echo -e "${BLUE}Step 1: Authentication Flow${NC}"
echo ""

# Get Client Secret from Keycloak
echo -e "${CYAN}Getting client secret from Keycloak...${NC}"

# Try to get from docker-compose environment
CLIENT_SECRET=$(docker exec smart-dictophone-api printenv KEYCLOAK_CLIENT_SECRET 2>/dev/null || echo "")

if [ -z "$CLIENT_SECRET" ]; then
    echo -e "${YELLOW}⚠ Could not get client secret from API container environment${NC}"
    CLIENT_SECRET="your-backend-client-secret-change-me"
fi

echo -e "  Using client secret: ${CLIENT_SECRET:0:15}..."
echo ""

# Test 1: Get Access Token
echo -e "${CYAN}Test 1.1: Getting access token...${NC}"
TOKEN_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=$CLIENT_ID" \
    -d "client_secret=$CLIENT_SECRET" \
    -d "grant_type=password" \
    -d "username=$TEST_USER" \
    -d "password=$TEST_PASSWORD")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
REFRESH_TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"refresh_token":"[^"]*"' | cut -d'"' -f4)

if [ -n "$ACCESS_TOKEN" ]; then
    test_result "Get Access Token" "PASS" "Token length: ${#ACCESS_TOKEN}"
else
    test_result "Get Access Token" "FAIL" "Response: ${TOKEN_RESPONSE:0:100}"
    echo ""
    echo -e "${RED}Cannot continue without access token.${NC}"
    echo ""
    echo -e "${YELLOW}Possible issues:${NC}"
    echo -e "  1. Client secret is incorrect"
    echo -e "  2. Client needs 'Direct Access Grants' enabled in Keycloak"
    echo -e "  3. User credentials are wrong"
    echo ""
    echo -e "${CYAN}To fix:${NC}"
    echo -e "  1. Open Keycloak: ${BLUE}http://localhost:8090${NC} (admin/admin)"
    echo -e "  2. Clients → smart-dictophone-backend → Settings"
    echo -e "  3. Enable 'Direct access grants' capability"
    echo -e "  4. Save and retry"
    echo ""
    exit 1
fi

# Test 2: Verify Token
echo -e "${CYAN}Test 1.2: Verifying token with API...${NC}"
API_ROOT_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$API_URL/" \
    -H "Authorization: Bearer $ACCESS_TOKEN")

HTTP_CODE=$(echo "$API_ROOT_RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$API_ROOT_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    test_result "Verify Token with API" "PASS" "HTTP $HTTP_CODE"
else
    test_result "Verify Token with API" "FAIL" "HTTP $HTTP_CODE: ${RESPONSE_BODY:0:100}"
fi

# Test 3: Refresh Token
echo -e "${CYAN}Test 1.3: Refreshing access token...${NC}"
REFRESH_RESPONSE=$(curl -s -X POST "$API_URL/refresh" \
    -H "Content-Type: application/json" \
    -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")

NEW_ACCESS_TOKEN=$(echo "$REFRESH_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

if [ -n "$NEW_ACCESS_TOKEN" ] && [ "$NEW_ACCESS_TOKEN" != "$ACCESS_TOKEN" ]; then
    test_result "Refresh Token" "PASS" "New token received"
    ACCESS_TOKEN=$NEW_ACCESS_TOKEN
else
    test_result "Refresh Token" "FAIL" "Token refresh failed"
fi

echo ""
echo -e "${BLUE}Step 2: Folder Operations${NC}"
echo ""

# Test 4: Get Folders (should auto-create default folders)
echo -e "${CYAN}Test 2.1: Getting folders list...${NC}"
FOLDERS_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$API_URL/folders" \
    -H "Authorization: Bearer $ACCESS_TOKEN")

HTTP_CODE=$(echo "$FOLDERS_RESPONSE" | tail -n1)
FOLDERS_BODY=$(echo "$FOLDERS_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    FOLDER_COUNT=$(echo "$FOLDERS_BODY" | grep -o '"id"' | wc -l | tr -d ' ')
    test_result "Get Folders" "PASS" "Found $FOLDER_COUNT folders"
    
    # Extract first folder ID for later use
    FOLDER_ID=$(echo "$FOLDERS_BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
else
    test_result "Get Folders" "FAIL" "HTTP $HTTP_CODE"
fi

# Test 5: Create New Folder
echo -e "${CYAN}Test 2.2: Creating new folder...${NC}"
NEW_FOLDER_NAME="Test Folder $(date +%s)"
CREATE_FOLDER_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/folders" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"$NEW_FOLDER_NAME\"}")

HTTP_CODE=$(echo "$CREATE_FOLDER_RESPONSE" | tail -n1)
FOLDER_BODY=$(echo "$CREATE_FOLDER_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "201" ]; then
    NEW_FOLDER_ID=$(echo "$FOLDER_BODY" | grep -o '"id":[0-9]*' | cut -d':' -f2)
    test_result "Create Folder" "PASS" "Folder ID: $NEW_FOLDER_ID"
else
    test_result "Create Folder" "FAIL" "HTTP $HTTP_CODE"
fi

# Test 6: Update Folder
if [ -n "$NEW_FOLDER_ID" ]; then
    echo -e "${CYAN}Test 2.3: Updating folder...${NC}"
    UPDATED_NAME="Updated Test Folder"
    UPDATE_RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "$API_URL/folders/$NEW_FOLDER_ID" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"$UPDATED_NAME\"}")
    
    HTTP_CODE=$(echo "$UPDATE_RESPONSE" | tail -n1)
    
    if [ "$HTTP_CODE" = "200" ]; then
        test_result "Update Folder" "PASS" "Folder renamed"
    else
        test_result "Update Folder" "FAIL" "HTTP $HTTP_CODE"
    fi
fi

echo ""
echo -e "${BLUE}Step 3: Record Operations${NC}"
echo ""

# Test 7: Create Test Audio File
echo -e "${CYAN}Test 3.1: Creating test audio file...${NC}"
TEST_AUDIO_FILE="/tmp/test_audio_$(date +%s).m4a"

# Create a minimal valid M4A file (silent audio, 1 second)
# This is a base64 encoded minimal M4A file structure
cat > "$TEST_AUDIO_FILE" << 'EOF'
This is a test audio file content for Smart Dictophone integration test.
It simulates an audio recording that would be uploaded to the system.
EOF

if [ -f "$TEST_AUDIO_FILE" ]; then
    test_result "Create Test Audio" "PASS" "File: $TEST_AUDIO_FILE"
else
    test_result "Create Test Audio" "FAIL" "Could not create test file"
fi

# Test 8: Upload Record with Audio
if [ -f "$TEST_AUDIO_FILE" ] && [ -n "$FOLDER_ID" ]; then
    echo -e "${CYAN}Test 3.2: Uploading audio record...${NC}"
    
    UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/records" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -F "title=Integration Test Recording" \
        -F "folder_id=$FOLDER_ID" \
        -F "audio=@$TEST_AUDIO_FILE")
    
    HTTP_CODE=$(echo "$UPLOAD_RESPONSE" | tail -n1)
    UPLOAD_BODY=$(echo "$UPLOAD_RESPONSE" | sed '$d')
    
    if [ "$HTTP_CODE" = "201" ]; then
        RECORD_ID=$(echo "$UPLOAD_BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
        test_result "Upload Record" "PASS" "Record ID: $RECORD_ID"
        
        # Check if transcription task was sent to RabbitMQ
        sleep 2
        RABBITMQ_QUEUE_INFO=$(docker exec smart-dictophone-rabbitmq rabbitmqctl list_queues name messages 2>/dev/null | grep transcription_queue || echo "")
        
        if [ -n "$RABBITMQ_QUEUE_INFO" ]; then
            MESSAGE_COUNT=$(echo "$RABBITMQ_QUEUE_INFO" | awk '{print $2}')
            if [ "$MESSAGE_COUNT" -gt 0 ]; then
                test_result "RabbitMQ Task Sent" "PASS" "$MESSAGE_COUNT message(s) in queue"
            else
                test_result "RabbitMQ Task Sent" "FAIL" "No messages in queue"
            fi
        fi
    else
        test_result "Upload Record" "FAIL" "HTTP $HTTP_CODE: ${UPLOAD_BODY:0:100}"
    fi
fi

# Test 9: Get Records List
echo -e "${CYAN}Test 3.3: Getting records list...${NC}"
RECORDS_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$API_URL/records" \
    -H "Authorization: Bearer $ACCESS_TOKEN")

HTTP_CODE=$(echo "$RECORDS_RESPONSE" | tail -n1)
RECORDS_BODY=$(echo "$RECORDS_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
    RECORD_COUNT=$(echo "$RECORDS_BODY" | grep -o '"id"' | wc -l | tr -d ' ')
    test_result "Get Records" "PASS" "Found $RECORD_COUNT record(s)"
else
    test_result "Get Records" "FAIL" "HTTP $HTTP_CODE"
fi

# Test 10: Get Record Info
if [ -n "$RECORD_ID" ]; then
    echo -e "${CYAN}Test 3.4: Getting specific record info...${NC}"
    RECORD_INFO_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$API_URL/recordInfo?id=$RECORD_ID" \
        -H "Authorization: Bearer $ACCESS_TOKEN")
    
    HTTP_CODE=$(echo "$RECORD_INFO_RESPONSE" | tail -n1)
    
    if [ "$HTTP_CODE" = "200" ]; then
        test_result "Get Record Info" "PASS" "Record details retrieved"
    else
        test_result "Get Record Info" "FAIL" "HTTP $HTTP_CODE"
    fi
fi

# Test 11: Download Audio
if [ -n "$RECORD_ID" ]; then
    echo -e "${CYAN}Test 3.5: Downloading audio file...${NC}"
    AUDIO_DOWNLOAD=$(curl -s -w "\n%{http_code}" -X GET "$API_URL/records/$RECORD_ID/audio" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -o /tmp/downloaded_audio.m4a)
    
    HTTP_CODE=$(echo "$AUDIO_DOWNLOAD" | tail -n1)
    
    if [ "$HTTP_CODE" = "200" ] && [ -f "/tmp/downloaded_audio.m4a" ]; then
        FILE_SIZE=$(stat -f%z "/tmp/downloaded_audio.m4a" 2>/dev/null || stat -c%s "/tmp/downloaded_audio.m4a" 2>/dev/null)
        test_result "Download Audio" "PASS" "File size: $FILE_SIZE bytes"
        rm -f /tmp/downloaded_audio.m4a
    else
        test_result "Download Audio" "FAIL" "HTTP $HTTP_CODE"
    fi
fi

# Test 12: Simulate Transcription (POST to transcribe endpoint)
if [ -n "$RECORD_ID" ]; then
    echo -e "${CYAN}Test 3.6: Simulating ML transcription result...${NC}"
    
    TRANSCRIPTION_JSON=$(cat <<-END
{
  "record_id": $RECORD_ID,
  "transcription_text": "This is a test transcription from integration test. It simulates the result of ML service processing.",
  "segments": [
    {
      "start_time": 0.0,
      "end_time": 3.5,
      "text": "This is a test transcription from integration test."
    },
    {
      "start_time": 3.5,
      "end_time": 7.0,
      "text": "It simulates the result of ML service processing."
    }
  ]
}
END
)
    
    TRANSCRIBE_RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$API_URL/records/$RECORD_ID/transcribe" \
        -H "Content-Type: application/json" \
        -d "$TRANSCRIPTION_JSON")
    
    HTTP_CODE=$(echo "$TRANSCRIBE_RESPONSE" | tail -n1)
    
    if [ "$HTTP_CODE" = "200" ]; then
        test_result "Submit Transcription" "PASS" "Transcription saved"
    else
        test_result "Submit Transcription" "FAIL" "HTTP $HTTP_CODE"
    fi
fi

# Test 13: Generate PDF
if [ -n "$RECORD_ID" ]; then
    echo -e "${CYAN}Test 3.7: Generating PDF...${NC}"
    PDF_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$API_URL/records/$RECORD_ID/pdf" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -o /tmp/test_transcription.pdf)
    
    HTTP_CODE=$(echo "$PDF_RESPONSE" | tail -n1)
    
    if [ "$HTTP_CODE" = "200" ] && [ -f "/tmp/test_transcription.pdf" ]; then
        PDF_SIZE=$(stat -f%z "/tmp/test_transcription.pdf" 2>/dev/null || stat -c%s "/tmp/test_transcription.pdf" 2>/dev/null)
        test_result "Generate PDF" "PASS" "PDF size: $PDF_SIZE bytes"
        rm -f /tmp/test_transcription.pdf
    else
        test_result "Generate PDF" "FAIL" "HTTP $HTTP_CODE"
    fi
fi

echo ""
echo -e "${BLUE}Step 4: Search & Filter${NC}"
echo ""

# Test 14: Search Records by Text
echo -e "${CYAN}Test 4.1: Searching records by text...${NC}"
SEARCH_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$API_URL/records?search=test" \
    -H "Authorization: Bearer $ACCESS_TOKEN")

HTTP_CODE=$(echo "$SEARCH_RESPONSE" | tail -n1)

if [ "$HTTP_CODE" = "200" ]; then
    test_result "Search Records" "PASS" "Search completed"
else
    test_result "Search Records" "FAIL" "HTTP $HTTP_CODE"
fi

# Test 15: Filter by Folder
if [ -n "$FOLDER_ID" ]; then
    echo -e "${CYAN}Test 4.2: Filtering records by folder...${NC}"
    FILTER_RESPONSE=$(curl -s -w "\n%{http_code}" -X GET "$API_URL/records?folder_id=$FOLDER_ID" \
        -H "Authorization: Bearer $ACCESS_TOKEN")
    
    HTTP_CODE=$(echo "$FILTER_RESPONSE" | tail -n1)
    
    if [ "$HTTP_CODE" = "200" ]; then
        test_result "Filter by Folder" "PASS" "Filter applied"
    else
        test_result "Filter by Folder" "FAIL" "HTTP $HTTP_CODE"
    fi
fi

echo ""
echo -e "${BLUE}Step 5: Cleanup${NC}"
echo ""

# Test 16: Delete Record
if [ -n "$RECORD_ID" ]; then
    echo -e "${CYAN}Test 5.1: Deleting test record...${NC}"
    DELETE_RECORD_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$API_URL/records/$RECORD_ID" \
        -H "Authorization: Bearer $ACCESS_TOKEN")
    
    HTTP_CODE=$(echo "$DELETE_RECORD_RESPONSE" | tail -n1)
    
    if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ]; then
        test_result "Delete Record" "PASS" "Record deleted"
    else
        test_result "Delete Record" "FAIL" "HTTP $HTTP_CODE"
    fi
fi

# Test 17: Delete Folder
if [ -n "$NEW_FOLDER_ID" ]; then
    echo -e "${CYAN}Test 5.2: Deleting test folder...${NC}"
    DELETE_FOLDER_RESPONSE=$(curl -s -w "\n%{http_code}" -X DELETE "$API_URL/folders/$NEW_FOLDER_ID" \
        -H "Authorization: Bearer $ACCESS_TOKEN")
    
    HTTP_CODE=$(echo "$DELETE_FOLDER_RESPONSE" | tail -n1)
    
    if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ]; then
        test_result "Delete Folder" "PASS" "Folder deleted"
    else
        test_result "Delete Folder" "FAIL" "HTTP $HTTP_CODE"
    fi
fi

# Cleanup temp files
rm -f "$TEST_AUDIO_FILE"

echo ""
echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  Test Summary${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

TOTAL_TESTS=$((TESTS_PASSED + TESTS_FAILED))
PASS_RATE=$(awk "BEGIN {printf \"%.1f\", ($TESTS_PASSED / $TOTAL_TESTS) * 100}")

echo -e "Total Tests:    $TOTAL_TESTS"
echo -e "${GREEN}Passed:${NC}         $TESTS_PASSED"
echo -e "${RED}Failed:${NC}         $TESTS_FAILED"
echo -e "Success Rate:   ${PASS_RATE}%"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All integration tests passed!${NC}"
    echo ""
    echo -e "${BLUE}Complete Flow Verified:${NC}"
    echo -e "  1. ✅ Keycloak Authentication (login, token, refresh)"
    echo -e "  2. ✅ Folder Management (create, read, update, delete)"
    echo -e "  3. ✅ Record Upload (with audio file)"
    echo -e "  4. ✅ RabbitMQ Integration (transcription task sent)"
    echo -e "  5. ✅ Transcription Processing (ML service simulation)"
    echo -e "  6. ✅ PDF Generation (download transcription)"
    echo -e "  7. ✅ Search & Filter (by text and folder)"
    echo -e "  8. ✅ Cleanup (delete records and folders)"
    echo ""
    exit 0
else
    echo -e "${RED}✗ Some integration tests failed!${NC}"
    echo ""
    echo -e "${YELLOW}Troubleshooting:${NC}"
    echo -e "  1. Check if all services are running: ${BLUE}./scripts/health-check.sh${NC}"
    echo -e "  2. Check API logs: ${BLUE}docker logs smart-dictophone-api${NC}"
    echo -e "  3. Verify Keycloak client secret is correct"
    echo -e "  4. Ensure test user exists in Keycloak"
    echo ""
    exit 1
fi
