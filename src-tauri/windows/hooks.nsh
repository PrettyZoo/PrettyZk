; Tauri NSIS installer hooks.
;
; Everything the app needs at runtime is bundled in the installer (JRE, jars,
; offline WebView2). The only optional system component is the Visual C++
; runtime used by the Rust binary; if vc_redist.x64.exe was staged next to the
; application files, install it silently.

!macro NSIS_HOOK_POSTINSTALL
  IfFileExists "$INSTDIR\vc_redist.x64.exe" 0 +2
  ExecWait '"$INSTDIR\vc_redist.x64.exe" /install /quiet /norestart'
!macroend
