#!/bin/bash
# Setup script for Harness Engineering
# 安装 Harness Engineering 所需的工具和配置

set -e

echo "🚀 Setting up Harness Engineering for 基金管家..."

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}📦 Step 1: Installing Git hooks...${NC}"

# 创建 .git/hooks 目录（如果不存在）
mkdir -p .git/hooks

# 安装 pre-commit hook
if [ -f "scripts/pre-commit.sh" ]; then
    cp scripts/pre-commit.sh .git/hooks/pre-commit
    chmod +x .git/hooks/pre-commit
    echo -e "${GREEN}✅ Pre-commit hook installed${NC}"
else
    echo -e "${RED}❌ Pre-commit script not found${NC}"
    exit 1
fi

echo -e "${BLUE}📦 Step 2: Setting up Maven wrapper...${NC}"
if [ ! -f "./mvnw" ]; then
    echo -e "${YELLOW}⚠️  Maven wrapper not found, downloading...${NC}"
    mvn wrapper:wrapper
fi
echo -e "${GREEN}✅ Maven wrapper ready${NC}"

echo -e "${BLUE}📦 Step 3: Installing backend dependencies...${NC}"
./mvnw dependency:resolve -q
echo -e "${GREEN}✅ Backend dependencies installed${NC}"

echo -e "${BLUE}📦 Step 4: Installing frontend dependencies...${NC}"
cd fund-web
if [ ! -d "node_modules" ]; then
    npm install
fi
echo -e "${GREEN}✅ Frontend dependencies installed${NC}"
cd ..

echo -e "${BLUE}📦 Step 5: Verifying setup...${NC}"

# 检查 Checkstyle 配置
if [ ! -f "checkstyle.xml" ]; then
    echo -e "${RED}❌ checkstyle.xml not found${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Checkstyle configuration verified${NC}"

# 检查 AGENTS.md
if [ ! -f "AGENTS.md" ]; then
    echo -e "${RED}❌ AGENTS.md not found${NC}"
    exit 1
fi
echo -e "${GREEN}✅ AGENTS.md verified${NC}"

# 检查 CI 配置
if [ ! -f ".github/workflows/ci.yml" ]; then
    echo -e "${RED}❌ CI workflow not found${NC}"
    exit 1
fi
echo -e "${GREEN}✅ CI configuration verified${NC}"

echo ""
echo -e "${GREEN}🎉 Harness Engineering setup complete!${NC}"
echo ""
echo "📋 Quick start:"
echo "  1. Read AGENTS.md for project guidelines"
echo "  2. Run './mvnw test' to verify backend"
echo "  3. Run 'cd fund-web && npm run dev' to start frontend"
echo "  4. Make changes and 'git commit' (hooks will auto-check)"
echo ""
echo "🔗 Useful commands:"
echo "  ./mvnw checkstyle:check    - Check Java code style"
echo "  ./mvnw test                - Run all tests"
echo "  cd fund-web && npm run lint - Check frontend code"
echo ""
