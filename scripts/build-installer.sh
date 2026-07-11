#!/bin/bash
# PrettyZk - Build self-contained installer
set -e
export PATH="$HOME/.cargo/bin:$PATH"
cd "$(dirname "$0")/.."
echo "=== PrettyZk Installer Build ==="

# 1. Build backend
echo "[1/5] Building backend..."
./gradlew :app:installDist -q

# 2. Build frontend
echo "[2/5] Building frontend..."
cd webapp && npm install --silent && npm run build --silent && cd ..

# 3. Create minimal JRE with jlink
echo "[3/5] Creating minimal JRE..."
JLINK_BASE="build/jlink-runtime"
rm -rf "$JLINK_BASE"

# Get the module path from current JDK
MODULE_PATH="$JAVA_HOME/jmods"

# Required JDK modules for our app
ALL_MODS="java.base,java.logging,java.naming,java.management,java.instrument,java.security.jgss,java.net.http,java.scripting,java.xml,jdk.unsupported"

echo "  Modules: $ALL_MODS"

# Create runtime image
jlink --module-path "$MODULE_PATH" \
  --add-modules "$ALL_MODS" \
  --strip-debug --compress 2 --no-header-files --no-man-pages \
  --output "$JLINK_BASE"

echo "  JRE size: $(du -sh "$JLINK_BASE" | cut -f1)"

# 4. Bundle into .app
echo "[4/5] Bundling installer..."
APP="src-tauri/target/release/bundle/macos/PrettyZk.app"
rm -rf "$APP" 2>/dev/null

# Build Tauri binary first
cd src-tauri && cargo build --release --quiet && cd ..

# Create .app structure manually (faster than tauri build)
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources/app/lib" \
  "$APP/Contents/Resources/runtime" "$APP/Contents/Resources/icons"

# Copy binary
cp "src-tauri/target/release/prettyzoo" "$APP/Contents/MacOS/"

# Copy backend distribution
cp -r "app/build/install/app/lib/" "$APP/Contents/Resources/app/lib/"
cp -r "app/build/install/app/bin/" "$APP/Contents/Resources/app/bin/"

# Copy JRE
cp -r "$JLINK_BASE"/* "$APP/Contents/Resources/runtime/"

# Copy icons
cp src-tauri/icons/icon.icns "$APP/Contents/Resources/"

# Create Info.plist
cat > "$APP/Contents/Info.plist" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key>
    <string>prettyzoo</string>
    <key>CFBundleIdentifier</key>
    <string>cc.cc1234.prettyzk</string>
    <key>CFBundleName</key>
    <string>PrettyZk</string>
    <key>CFBundleVersion</key>
    <string>3.0.0</string>
    <key>CFBundleShortVersionString</key>
    <string>3.0.0</string>
    <key>CFBundleIconFile</key>
    <string>icon.icns</string>
    <key>CFBundlePackageType</key>
    <string>APPL</string>
    <key>LSMinimumSystemVersion</key>
    <string>12.0</string>
    <key>NSHighResolutionCapable</key>
    <true/>
</dict>
</plist>
EOF

# 5. Create DMG
echo "[5/5] Creating DMG..."
DMG_NAME="PrettyZk_3.0.0_x64.dmg"
rm -f "$DMG_NAME"

hdiutil create -volname "PrettyZk" -srcfolder "$APP" \
  -ov -format UDZO -size 200m "$DMG_NAME" 2>/dev/null

mv "$DMG_NAME" "../$DMG_NAME" 2>/dev/null || true

echo ""
echo "=== Build Complete ==="
echo "Installer: $(pwd)/$DMG_NAME"
echo "Size: $(du -sh "$DMG_NAME" | cut -f1)"
echo ""
echo "To install: open $DMG_NAME and drag PrettyZk.app to Applications"
echo "If blocked by Gatekeeper: xattr -cr /Applications/PrettyZk.app"
