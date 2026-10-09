Unicode True

!include "MUI2.nsh"
!include "LogicLib.nsh"
!include "x64.nsh"

!ifndef APP_VERSION
  !error "APP_VERSION is required"
!endif
!ifndef SOURCE_COMMIT
  !error "SOURCE_COMMIT is required"
!endif
!ifndef SOURCE_DIR
  !error "SOURCE_DIR is required"
!endif
!ifndef OUTPUT_FILE
  !error "OUTPUT_FILE is required"
!endif

Name "LoongSim ${APP_VERSION}"
OutFile "${OUTPUT_FILE}"
InstallDir "$LOCALAPPDATA\Programs\LoongSim"
InstallDirRegKey HKCU "Software\LoongSim" "InstallDir"
RequestExecutionLevel user

VIProductVersion "${APP_VERSION}.0"
VIAddVersionKey /LANG=2052 "ProductName" "LoongSim"
VIAddVersionKey /LANG=2052 "ProductVersion" "${APP_VERSION}"
VIAddVersionKey /LANG=2052 "FileDescription" "LoongSim Logisim-only Windows x64 installer"
VIAddVersionKey /LANG=2052 "FileVersion" "${APP_VERSION}"
VIAddVersionKey /LANG=2052 "LegalCopyright" "LoongSim and Logisim-evolution contributors"
VIAddVersionKey /LANG=2052 "Comments" "Built from commit ${SOURCE_COMMIT}"

SetCompressor /SOLID lzma
SetCompressorDictSize 64
ShowInstDetails show
ShowUninstDetails show

!define MUI_ABORTWARNING
!define MUI_ICON "${NSISDIR}\Contrib\Graphics\Icons\orange-install.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\orange-uninstall.ico"
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_FUNCTION LaunchLoongSim
!define MUI_FINISHPAGE_RUN_TEXT "运行 LoongSim"

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

!insertmacro MUI_LANGUAGE "SimpChinese"
!insertmacro MUI_LANGUAGE "English"

Function .onInit
  ${IfNot} ${RunningX64}
    MessageBox MB_ICONSTOP "此安装包仅支持 64 位 Windows。"
    Abort
  ${EndIf}
FunctionEnd

Function LaunchLoongSim
  Exec '"$INSTDIR\runtime\bin\javaw.exe" -Dflatlaf.useNativeLibrary=false -Dflatlaf.useSystemFileChooser=false -jar "$INSTDIR\LoongSim.jar"'
FunctionEnd

Section "LoongSim" SecLoongSim
  SectionIn RO
  SetOutPath "$INSTDIR"

  File "${SOURCE_DIR}\LoongSim.jar"
  File "${SOURCE_DIR}\LICENSE.md"
  File "${SOURCE_DIR}\README.txt"
  File "${SOURCE_DIR}\VERSION.txt"
  File /r "${SOURCE_DIR}\runtime"

  WriteUninstaller "$INSTDIR\Uninstall.exe"

  CreateDirectory "$SMPROGRAMS\LoongSim"
  CreateShortCut "$SMPROGRAMS\LoongSim\LoongSim.lnk" \
    "$INSTDIR\runtime\bin\javaw.exe" \
    '-Dflatlaf.useNativeLibrary=false -Dflatlaf.useSystemFileChooser=false -jar "$INSTDIR\LoongSim.jar"' \
    "$INSTDIR\runtime\bin\javaw.exe" 0 SW_SHOWNORMAL "" "LoongSim"
  CreateShortCut "$SMPROGRAMS\LoongSim\卸载 LoongSim.lnk" \
    "$INSTDIR\Uninstall.exe" "" "$INSTDIR\Uninstall.exe" 0
  CreateShortCut "$DESKTOP\LoongSim.lnk" \
    "$INSTDIR\runtime\bin\javaw.exe" \
    '-Dflatlaf.useNativeLibrary=false -Dflatlaf.useSystemFileChooser=false -jar "$INSTDIR\LoongSim.jar"' \
    "$INSTDIR\runtime\bin\javaw.exe" 0 SW_SHOWNORMAL "" "LoongSim"

  WriteRegStr HKCU "Software\LoongSim" "InstallDir" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LoongSim" \
    "DisplayName" "LoongSim ${APP_VERSION} (Logisim-only)"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LoongSim" \
    "DisplayVersion" "${APP_VERSION}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LoongSim" \
    "Publisher" "LoongSim"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LoongSim" \
    "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LoongSim" \
    "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LoongSim" \
    "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LoongSim" \
    "NoRepair" 1
SectionEnd

Section "Uninstall"
  Delete "$DESKTOP\LoongSim.lnk"
  Delete "$SMPROGRAMS\LoongSim\LoongSim.lnk"
  Delete "$SMPROGRAMS\LoongSim\卸载 LoongSim.lnk"
  RMDir "$SMPROGRAMS\LoongSim"

  Delete "$INSTDIR\LoongSim.jar"
  Delete "$INSTDIR\LICENSE.md"
  Delete "$INSTDIR\README.txt"
  Delete "$INSTDIR\VERSION.txt"
  RMDir /r "$INSTDIR\runtime"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"

  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\LoongSim"
  DeleteRegKey HKCU "Software\LoongSim"
SectionEnd
