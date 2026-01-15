#!/bin/bash
# setup-hooks.sh - Installs git pre-commit hooks for LedgerLens
#
# This script sets up pre-commit hooks to run ktlint and detekt
# before each commit, ensuring code quality standards are met.
#
# Usage: ./scripts/setup-hooks.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
HOOKS_DIR="$PROJECT_DIR/.git/hooks"
PRE_COMMIT="$HOOKS_DIR/pre-commit"

echo "Setting up git hooks for LedgerLens..."

# Check if we're in a git repository
if [ ! -d "$PROJECT_DIR/.git" ]; then
    echo "Error: Not a git repository. Please run this from the project root."
    exit 1
fi

# Create hooks directory if it doesn't exist
mkdir -p "$HOOKS_DIR"

# Create pre-commit hook
cat > "$PRE_COMMIT" << 'EOF'
#!/bin/sh
# Pre-commit hook for LedgerLens
# Runs ktlint and detekt checks before allowing commits

echo "🔍 Running pre-commit checks..."
echo ""

# Get the project root directory
PROJECT_ROOT="$(git rev-parse --show-toplevel)"
cd "$PROJECT_ROOT"

# Run ktlint check
echo "📝 Running ktlint..."
./gradlew ktlintCheck --daemon --quiet
KTLINT_STATUS=$?

if [ $KTLINT_STATUS -ne 0 ]; then
    echo ""
    echo "❌ ktlint check failed!"
    echo "   Run './gradlew ktlintFormat' to auto-fix formatting issues."
    echo ""
    exit 1
fi
echo "✅ ktlint check passed"

# Run detekt
echo ""
echo "🔎 Running detekt..."
./gradlew detekt --daemon --quiet
DETEKT_STATUS=$?

if [ $DETEKT_STATUS -ne 0 ]; then
    echo ""
    echo "❌ detekt check failed!"
    echo "   Please fix the reported issues before committing."
    echo ""
    exit 1
fi
echo "✅ detekt check passed"

echo ""
echo "✨ All pre-commit checks passed!"
exit 0
EOF

# Make the hook executable
chmod +x "$PRE_COMMIT"

echo "✅ Pre-commit hook installed successfully!"
echo ""
echo "The following checks will run before each commit:"
echo "  - ktlint (code formatting)"
echo "  - detekt (static analysis)"
echo ""
echo "To skip hooks temporarily (not recommended), use: git commit --no-verify"
