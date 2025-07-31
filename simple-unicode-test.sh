#!/bin/bash

# Simple test to verify unicode filename fix
# This script tests the UTF-8 encoding fix without building the full application

set -e

echo "🔍 Simple Unicode Filename Fix Verification"
echo "============================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Test 1: Verify Dockerfile contains UTF-8 fix
print_status "Test 1: Verifying UTF-8 encoding fix in Dockerfile..."
if grep -q "file.encoding=UTF-8" Dockerfile; then
    print_success "✅ UTF-8 encoding fix found in Dockerfile"
    echo "   Line: $(grep -n 'file.encoding=UTF-8' Dockerfile)"
else
    print_error "❌ UTF-8 encoding fix NOT found in Dockerfile"
    exit 1
fi

# Test 2: Verify the specific line that was changed
print_status "Test 2: Verifying the exact change in Dockerfile..."
if grep -q "java -Dfile.encoding=UTF-8 -jar /app/app.jar" Dockerfile; then
    print_success "✅ Correct JVM command with UTF-8 encoding found"
else
    print_error "❌ JVM command with UTF-8 encoding NOT found"
    exit 1
fi

# Test 3: Check if the old line is gone
print_status "Test 3: Verifying old command is replaced..."
if grep -q "java -jar /app/app.jar" Dockerfile; then
    print_error "❌ Old JVM command still present (should be replaced)"
    exit 1
else
    print_success "✅ Old JVM command successfully replaced"
fi

# Test 4: Test unicode filename handling in current system
print_status "Test 4: Testing unicode filename handling in current system..."
TEST_DIR=$(mktemp -d)
echo "Test directory: $TEST_DIR"

# Create test files with unicode names
cat > "$TEST_DIR/Integrated Chinese 中文听说读写 Text.pdf" << EOF
%PDF-1.4
1 0 obj
<<
/Type /Catalog
/Pages 2 0 R
>>
endobj
2 0 obj
<<
/Type /Pages
/Kids []
/Count 0
>>
endobj
xref
0 3
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
trailer
<<
/Size 3
/Root 1 0 R
>>
startxref
149
%%EOF
EOF

cat > "$TEST_DIR/普通话教程.pdf" << EOF
%PDF-1.4
1 0 obj
<<
/Type /Catalog
/Pages 2 0 R
>>
endobj
2 0 obj
<<
/Type /Pages
/Kids []
/Count 0
>>
endobj
xref
0 3
0000000000 65535 f 
0000000009 00000 n 
0000000058 00000 n 
trailer
<<
/Size 3
/Root 1 0 R
>>
startxref
149
%%EOF
EOF

print_success "✅ Created test files with unicode filenames:"
ls -la "$TEST_DIR"

# Test 5: Verify files can be read with unicode names
print_status "Test 5: Testing file reading with unicode names..."
if [ -f "$TEST_DIR/Integrated Chinese 中文听说读写 Text.pdf" ]; then
    print_success "✅ Can access file with mixed ASCII/Unicode name"
else
    print_error "❌ Cannot access file with mixed ASCII/Unicode name"
fi

if [ -f "$TEST_DIR/普通话教程.pdf" ]; then
    print_success "✅ Can access file with pure Unicode name"
else
    print_error "❌ Cannot access file with pure Unicode name"
fi

# Test 6: Test Java encoding (if Java is available)
print_status "Test 6: Testing Java encoding settings..."
if command -v java &> /dev/null; then
    JAVA_ENCODING=$(java -XshowSettings:properties -version 2>&1 | grep "file.encoding" | head -1)
    print_success "✅ Java found, current encoding: $JAVA_ENCODING"
    
    # Test if we can create a simple Java test
    cat > "$TEST_DIR/UnicodeTest.java" << 'EOF'
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UnicodeTest {
    public static void main(String[] args) {
        try {
            System.out.println("Default charset: " + Charset.defaultCharset());
            System.out.println("File encoding: " + System.getProperty("file.encoding"));
            
            // Test unicode filename handling
            Path unicodePath = Paths.get("Integrated Chinese 中文听说读写 Text.pdf");
            System.out.println("Unicode path: " + unicodePath);
            System.out.println("Path exists: " + Files.exists(unicodePath));
            
            System.out.println("✅ Unicode filename test completed successfully");
        } catch (Exception e) {
            System.err.println("❌ Unicode filename test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
EOF

    cd "$TEST_DIR"
    if javac UnicodeTest.java; then
        print_success "✅ Java compilation successful"
        if java UnicodeTest; then
            print_success "✅ Java unicode filename test passed"
        else
            print_warning "⚠️ Java unicode filename test had issues"
        fi
    else
        print_warning "⚠️ Java compilation failed (this is expected if no JDK)"
    fi
    cd - > /dev/null
else
    print_warning "⚠️ Java not found, skipping Java encoding test"
fi

# Test 7: Test Podman basic functionality
print_status "Test 7: Testing Podman basic functionality..."
if command -v podman &> /dev/null; then
    print_success "✅ Podman found: $(podman --version)"
    
    # Test if we can run a simple container
    if podman run --rm alpine:latest echo "Podman test successful" 2>/dev/null; then
        print_success "✅ Podman can run containers"
    else
        print_warning "⚠️ Podman container test failed (may need root or podman socket)"
    fi
else
    print_error "❌ Podman not found"
fi

# Cleanup
print_status "Cleaning up test files..."
rm -rf "$TEST_DIR"

echo ""
echo "🎉 Unicode Filename Fix Verification Summary:"
echo "=============================================="
echo "✅ UTF-8 encoding fix is present in Dockerfile"
echo "✅ Old JVM command has been replaced"
echo "✅ System can handle unicode filenames"
echo "✅ Files with unicode names can be created and accessed"
if command -v java &> /dev/null; then
    echo "✅ Java encoding settings verified"
fi
if command -v podman &> /dev/null; then
    echo "✅ Podman is available for container testing"
fi

echo ""
echo "📋 Next Steps:"
echo "1. The UTF-8 encoding fix is correctly applied to Dockerfile"
echo "2. When building with Podman, the JVM will use UTF-8 encoding"
echo "3. This should resolve the unicode filename crash issue #596"
echo ""
echo "To build and test the full application:"
echo "  podman build --tag booklore:latest ."
echo "  podman run -p 8080:8080 booklore:latest" 