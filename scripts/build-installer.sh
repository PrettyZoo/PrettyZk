#!/bin/bash
# PrettyZk - Build self-contained installer (macOS)
set -e
export PATH="$HOME/.cargo/bin:$PATH"
cd "$(dirname "$0")/.."

# Derive version from git tag, fallback to env var or default
VERSION="${VERSION:-$(git describe --tags --abbrev=0 2>/dev/null | sed 's/^v//' || echo '3.0.0')}"
echo "=== PrettyZk v${VERSION} Installer Build ==="

echo "[1/6] Building frontend..."
cd webapp && npm install --silent && npm run build --silent && cd ..

echo "[2/6] Building backend..."
./gradlew :app:installDist -q

echo "[3/6] Creating minimal JRE..."
rm -rf build/jlink-runtime
jlink --module-path "$JAVA_HOME/jmods" \
  --add-modules java.base,java.desktop,java.logging,java.naming,java.management,java.instrument,java.security.jgss,java.net.http,java.scripting,java.xml,jdk.unsupported \
  --strip-debug --no-header-files --no-man-pages \
  --output build/jlink-runtime
rm -rf build/jlink-runtime/legal
echo "  JRE: $(du -sh build/jlink-runtime | cut -f1)"

echo "[4/6] Building Tauri..."
cd src-tauri && cargo build --release --quiet && cd ..

echo "[5/6] Bundling .app..."
APP="src-tauri/target/release/bundle/macos/PrettyZk.app"
mkdir -p "$APP/Contents/Resources/runtime" "$APP/Contents/Resources/app/lib"
cp -R build/jlink-runtime/* "$APP/Contents/Resources/runtime/"
cp app/build/install/app/lib/*.jar "$APP/Contents/Resources/app/lib/"

# Create Info.plist if missing
if [ ! -f "$APP/Contents/Info.plist" ]; then
  cat > "$APP/Contents/Info.plist" << 'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleExecutable</key><string>prettyzoo</string>
<key>CFBundleIdentifier</key><string>cc.cc1234.prettyzk</string>
<key>CFBundleName</key><string>PrettyZk</string>
<key>CFBundleVersion</key><string>${VERSION}</string>
<key>CFBundleShortVersionString</key><string>${VERSION}</string>
<key>CFBundleIconFile</key><string>icon.icns</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>LSMinimumSystemVersion</key><string>12.0</string>
<key>NSHighResolutionCapable</key><true/>
</dict></plist>
PLIST
fi

echo "  App: $(du -sh $APP | cut -f1)"

echo "[6/6] Creating DMG..."
DMG_DIR="/tmp/prettyzk-dmg"
rm -rf "$DMG_DIR"
mkdir -p "$DMG_DIR"
cp -R "$APP" "$DMG_DIR/PrettyZk.app"
ln -s /Applications "$DMG_DIR/Applications"

rm -f "PrettyZk_${VERSION}.dmg"
hdiutil create -volname "PrettyZk" -srcfolder "$DMG_DIR" \
  -ov -format UDZO "PrettyZk_${VERSION}.dmg" 2>/dev/null
rm -rf "$DMG_DIR"

echo ""
echo "==========================================="
echo "  Build Complete!"
echo "  Installer: $(pwd)/PrettyZk_${VERSION}.dmg"
echo "  Size: $(du -sh PrettyZk_${VERSION}.dmg | cut -f1)"
echo "==========================================="
echo ""
echo "Install:"
echo "  open PrettyZk_${VERSION}.dmg"
echo "  Drag PrettyZk.app to Applications"
echo "  Then: xattr -cr /Applications/PrettyZk.app"
