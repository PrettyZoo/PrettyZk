Unicode True
!include "MUI2.nsh"

; Version: inject via makensis -DVERSION=x.y.z, defaults to 3.0.0
!ifndef VERSION
  !define VERSION "3.0.0"
!endif

Name "PrettyZk ${VERSION}"
OutFile "PrettyZk-Setup.exe"
RequestExecutionLevel admin
InstallDir "$PROGRAMFILES64\PrettyZk"

!define PRODUCT_NAME "PrettyZk"
!define PRODUCT_PUBLISHER "vran"
!define PRODUCT_UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}"

Section "Install"
  SetOutPath "$INSTDIR"
  File /r "stage\*.*"

  ; Install WebView2 runtime (skips if already present)
  DetailPrint "Ensuring WebView2 Runtime is installed..."
  nsExec::ExecToStack '"$INSTDIR\MicrosoftEdgeWebview2Setup.exe" /silent /install'
  Pop $0
  ${If} $0 != 0
    MessageBox MB_OK|MB_ICONINFORMATION "WebView2 installation returned exit code $0. The app may fail to start if WebView2 is missing."
  ${EndIf}
  Delete "$INSTDIR\MicrosoftEdgeWebview2Setup.exe"

  ; Create Start Menu shortcuts
  CreateDirectory "$SMPROGRAMS\${PRODUCT_NAME}"
  CreateShortcut "$SMPROGRAMS\${PRODUCT_NAME}\PrettyZk.lnk" "$INSTDIR\prettyzoo.exe"
  CreateShortcut "$SMPROGRAMS\${PRODUCT_NAME}\Uninstall.lnk" "$INSTDIR\uninstall.exe"

  ; Write uninstall registry entries for Add/Remove Programs
  WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "DisplayName" "${PRODUCT_NAME} ${VERSION}"
  WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "UninstallString" '"$INSTDIR\uninstall.exe"'
  WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "DisplayIcon" '"$INSTDIR\prettyzoo.exe",0'
  WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "Publisher" "${PRODUCT_PUBLISHER}"
  WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKLM "${PRODUCT_UNINST_KEY}" "InstallLocation" "$INSTDIR"
  WriteRegDWORD HKLM "${PRODUCT_UNINST_KEY}" "NoModify" 1
  WriteRegDWORD HKLM "${PRODUCT_UNINST_KEY}" "NoRepair" 1

  WriteUninstaller "$INSTDIR\uninstall.exe"
SectionEnd

Section "uninstall"
  ; Remove Start Menu shortcuts
  Delete "$SMPROGRAMS\${PRODUCT_NAME}\PrettyZk.lnk"
  Delete "$SMPROGRAMS\${PRODUCT_NAME}\Uninstall.lnk"
  RMDir "$SMPROGRAMS\${PRODUCT_NAME}"
  ; Clean up registry
  DeleteRegKey HKLM "${PRODUCT_UNINST_KEY}"
  ; Remove install directory
  RMDir /r "$INSTDIR"
SectionEnd
