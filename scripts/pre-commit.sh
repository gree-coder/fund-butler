#!/bin/bash
# Pre-commit hook for Harness Engineering
# 在提交前自动运行代码检查

set -e

echo "🔍 Running pre-commit checks..."

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查 Java 代码风格
echo "📋 Checking Java code style..."
if ! ./mvnw checkstyle:check -q; then
    echo -e "${RED}❌ Java code style check failed${NC}"
    echo "Run './mvnw checkstyle:check' to see details"
    exit 1
fi
echo -e "${GREEN}✅ Java code style check passed${NC}"

# 运行 Java 测试
echo "🧪 Running Java tests..."
if ! ./mvnw test -q; then
    echo -e "${RED}❌ Java tests failed${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Java tests passed${NC}"

# 检查前端代码（如果前端有修改）
if git diff --cached --name-only | grep -q "fund-web/"; then
    echo "📋 Checking frontend code..."
    cd fund-web
    
    if ! npm run lint; then
        echo -e "${RED}❌ Frontend lint failed${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ Frontend lint passed${NC}"
    
    if ! npx tsc --noEmit; then
        echo -e "${RED}❌ Frontend type check failed${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ Frontend type check passed${NC}"
    
    cd ..
fi

# 架构约束检查
echo "🏗️  Checking architecture constraints..."

# 检查 Controller 是否直接调用 Mapper
if git diff --cached --name-only | grep -q "src/main/java/com/qoder/fund/controller/"; then
    if grep -r "@Autowired.*Mapper" src/main/java/com/qoder/fund/controller/ 2>/dev/null; then
        echo -e "${RED}❌ Architecture violation: Controller should not directly depend on Mapper${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}✅ Architecture constraints passed${NC}"

# 检查提交信息格式
echo "📝 Checking commit message format..."
COMMIT_MSG_FILE=$(git rev-parse --git-dir)/COMMIT_EDITMSG
if [ -f "$COMMIT_MSG_FILE" ]; then
    COMMIT_MSG=$(head -n1 "$COMMIT_MSG_FILE")
    if ! echo "$COMMIT_MSG" | grep -qE "^(feat|fix|docs|style|refactor|test|chore|ci|build)(\(.+\))?: .+"; then
        echo -e "${YELLOW}⚠️  Warning: Commit message should follow conventional commits format${NC}"
        echo "Format: <type>(<scope>): <description>"
        echo "Types: feat, fix, docs, style, refactor, test, chore, ci, build"
    fi
fi

echo -e "${GREEN}✅ All pre-commit checks passed!${NC}"
echo "🚀 Ready to commit"
