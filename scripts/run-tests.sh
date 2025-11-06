#!/bin/bash

# Smart Dictophone - Unit and Integration Tests Runner
# This script runs all automated tests for the backend API

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  Smart Dictophone - Test Suite${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# Check if Gradle is available
if ! command -v ./gradlew &> /dev/null; then
    echo -e "${RED}✗ Gradle wrapper not found${NC}"
    echo -e "${YELLOW}  Run this script from project root directory${NC}"
    exit 1
fi

echo -e "${CYAN}Starting test execution...${NC}"
echo ""

# Run tests with Gradle
./gradlew test --no-daemon --console=plain 2>&1 | tee test-output.log

# Check exit code
TEST_EXIT_CODE=${PIPESTATUS[0]}

echo ""
echo -e "${BLUE}================================================${NC}"

if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    echo ""
    
    # Count test results
    PASSED=$(grep -c "PASSED" test-output.log || echo "0")
    FAILED=$(grep -c "FAILED" test-output.log || echo "0")
    SKIPPED=$(grep -c "SKIPPED" test-output.log || echo "0")
    
    echo -e "${CYAN}Test Summary:${NC}"
    echo -e "  ${GREEN}✓${NC} Passed:  $PASSED"
    echo -e "  ${YELLOW}⊘${NC} Skipped: $SKIPPED"
    echo -e "  ${RED}✗${NC} Failed:  $FAILED"
    echo ""
    
    # Show test report location
    echo -e "${CYAN}Detailed HTML report:${NC}"
    echo -e "  ${BLUE}build/reports/tests/test/index.html${NC}"
    echo ""
    
    # Open report in browser (optional)
    if [ "$1" = "--open" ] || [ "$1" = "-o" ]; then
        if command -v open &> /dev/null; then
            open build/reports/tests/test/index.html
        elif command -v xdg-open &> /dev/null; then
            xdg-open build/reports/tests/test/index.html
        fi
    fi
else
    echo -e "${RED}✗ Some tests failed${NC}"
    echo ""
    echo -e "${YELLOW}Failed tests:${NC}"
    grep "FAILED" test-output.log || echo "  (check test-output.log for details)"
    echo ""
    echo -e "${CYAN}To see detailed error messages:${NC}"
    echo -e "  ${BLUE}cat test-output.log${NC}"
    echo -e "  ${BLUE}open build/reports/tests/test/index.html${NC}"
    echo ""
fi

echo -e "${BLUE}================================================${NC}"

# Clean up log file
rm -f test-output.log

exit $TEST_EXIT_CODE
