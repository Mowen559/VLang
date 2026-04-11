@echo off
REM ==========================================================
REM VLang Native Engine Build Script (Windows/MinGW)
REM 工业化自举编译脚本
REM VLang-SH: self-hosting-v1.0
REM ==========================================================

SET GPP=F:\2\mingw\mingw64\bin\g++.exe
SET JAVA_HOME_AUTO=%JAVA_HOME%

REM 自动检测 JAVA_HOME（若未设置，尝试从注册表读取）
IF "%JAVA_HOME_AUTO%"=="" (
    FOR /F "tokens=2*" %%a IN ('reg query "HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\JDK" /v CurrentVersion 2^>nul') DO SET JDK_VER=%%b
    FOR /F "tokens=2*" %%a IN ('reg query "HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\JDK\%JDK_VER%" /v JavaHome 2^>nul') DO SET JAVA_HOME_AUTO=%%b
)

IF "%JAVA_HOME_AUTO%"=="" (
    echo [ERROR] JAVA_HOME not found. Please set JAVA_HOME environment variable.
    exit /b 1
)

echo [VLang Bootstrap] Using JDK: %JAVA_HOME_AUTO%
echo [VLang Bootstrap] Using G++: %GPP%

SET SRC_DIR=%~dp0src
SET OUT_DIR=%~dp0out

IF NOT EXIST "%OUT_DIR%" mkdir "%OUT_DIR%"

echo [VLang Bootstrap] Compiling vnative_parser.cpp ...

"%GPP%" ^
    -shared ^
    -o "%OUT_DIR%\vnative_parser.dll" ^
    "%SRC_DIR%\vnative_parser.cpp" ^
    -I"%JAVA_HOME_AUTO%\include" ^
    -I"%JAVA_HOME_AUTO%\include\win32" ^
    -O2 ^
    -std=c++17 ^
    -static-libgcc ^
    -static-libstdc++ ^
    -Wl,--kill-at

IF %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed! Check G++ path and JDK include dirs.
    exit /b 1
)

echo [VLang Bootstrap] SUCCESS: %OUT_DIR%\vnative_parser.dll
echo [VLang Bootstrap] Self-hosting native engine ready.
