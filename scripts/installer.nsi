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

; Product metadata for Add/Remove Programs
!define PRODUCT_NAME "PrettyZk"
!define PRODUCT_PUBLISHER "vran"
!define PRODUCT_UNINST_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${PRODUCT_NAME}"

Section "Install"
  SetOutPath "$INSTDIR"
  File /r "stage\*.*"

  ; Run MSI with error handling
  DetailPrint "Installing PrettyZk..."
  nsExec::ExecToStack 'msiexec /i "$INSTDIR\app.msi" /qn'
  Pop $0  ; exit code
  Pop $1  ; output (optional detail)

  ${If} $0 != 0
    ${If} $0 != 3010  ; 3010 = ERROR_SUCCESS_REBOOT_REQUIRED (still successful)
      MessageBox MB_ICONSTOP "MSI installation failed with exit code $0.$\n$\nOutput: $1"
      Delete "$INSTDIR\app.msi"
      SetOutPath "$TEMP"
      RMDir /r "$INSTDIR"
      Quit
    ${EndIf}
  ${EndIf}

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
  ; Uninstall the MSI product first
  nsExec::ExecToLog 'msiexec /x "$INSTDIR\app.msi" /qn'
  ; Clean up registry
  DeleteRegKey HKLM "${PRODUCT_UNINST_KEY}"
  ; Remove install directory
  RMDir /r "$INSTDIR"
SectionEnd
