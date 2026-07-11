Unicode True
!include "MUI2.nsh"
Name "PrettyZk 3.0.0"
OutFile "PrettyZk-Setup.exe"
RequestExecutionLevel admin
InstallDir "$PROGRAMFILES64\PrettyZk"

Section "Install"
  SetOutPath "$INSTDIR"
  File /r "stage\*.*"
  ExecWait 'msiexec /i "$INSTDIR\app.msi" /qn'
  Delete "$INSTDIR\app.msi"
  WriteUninstaller "$INSTDIR\uninstall.exe"
SectionEnd

Section "uninstall"
  RMDir /r "$INSTDIR"
SectionEnd
