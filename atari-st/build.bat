@echo off
setlocal

if exist "C:\cygwin64\bin\bash.exe" (
    set "CYGWIN_BASH=C:\cygwin64\bin\bash.exe"
) else if exist "C:\cygwin\bin\bash.exe" (
    set "CYGWIN_BASH=C:\cygwin\bin\bash.exe"
) else (
    echo Cygwin bash.exe was not found in a standard Cygwin installation. 1>&2
    exit /b 1
)

pushd "%~dp0"
"%CYGWIN_BASH%" -c "PATH=/usr/bin:/bin:$PATH; mkdir -p ../out/artifacts && m68k-atari-mintelf-gcc -O2 -g -Wall -Wextra -std=c99 -o ../out/artifacts/ballered.prg src/main.c src/menu.c src/about_dialog.c src/app_resources.c src/document_window.c src/file_open.c src/app_alerts.c src/app_logger.c -lgem && cp src/BALLERED.RSC ../out/artifacts/BALLERED.RSC"
set "BUILD_RESULT=%ERRORLEVEL%"
popd
exit /b %BUILD_RESULT%
