@echo off
rem P11c2, Lauf C2 (Werkbuch HalligHooge Teil A, 12.12, Datei 57): Messblick im Bild: Auflage, Isolinien, Partikel, LIC (Pruefsteine 17-19).
rem 1 Renderer bauen; 2 AnalysisCheck (Pruefsteine 17, 18); 3 FrameCheck; 4 MessblickCheck (Pruefstein 19, Bilder); 5 BuehneCheck (ohne Messblick bitgleich).
rem Erwartete Dauer: rund 15 Minuten.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase11cC2_.txt"
set "L1=%ST%\Status_Phase11cC2_1.txt"
set "L2=%ST%\Status_Phase11cC2_2.txt"
set "L3=%ST%\Status_Phase11cC2_3.txt"
set "L4=%ST%\Status_Phase11cC2_4.txt"
set "L5=%ST%\Status_Phase11cC2_5.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P11c2 C2, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase11cC2_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase11cC2_2: AnalysisCheck
>"%L3%" echo Status_Phase11cC2_3: FrameCheck
>"%L4%" echo Status_Phase11cC2_4: MessblickCheck
>"%L5%" echo Status_Phase11cC2_5: BuehneCheck

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
powershell -NoProfile -Command "if ((Get-Item 'dist\Renderer.jar').LastWriteTime.Ticks -gt %T0%) { exit 0 } else { exit 1 }"
if errorlevel 1 (
  >>"%UE%" echo dist\Renderer.jar ist nicht neuer als der Start des Laufs
  set "N=1 Renderer bauen (jar nicht neu)"
  goto :abbruch
)
>>"%UE%" echo dist\Renderer.jar ist neu

set "N=2 AnalysisCheck"
set "L=%L2%"
set "C=%R% com.dan.render.analysis.demo.AnalysisCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 FrameCheck"
set "L=%L3%"
set "C=%R% com.dan.render.frame.demo.FrameCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 MessblickCheck"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.MessblickCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 BuehneCheck"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien und Atteste
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 20 >nul
goto :eof

:abbruch
>>"%UE%" echo.
>>"%UE%" echo ABBRUCH %DATE% %TIME% nach: %N%
echo.
echo Abbruch nach: %N%  - siehe %L%
timeout /t 60 >nul
exit /b 1

:lauf
if not exist "%~dp0STOPP.txt" goto :lauf2
echo [%DATE% %TIME%] STOPP  %N%
>>"%UE%" echo [%DATE% %TIME%] STOPP  %N%  - uebersprungen
set "E=99"
goto :eof
:lauf2
echo [%DATE% %TIME%] Start  %N%
>>"%UE%" echo [%DATE% %TIME%] Start  %N%
>>"%L%" echo.
>>"%L%" echo ^>%C%
%C% >>"%L%" 2>&1
set "E=%ERRORLEVEL%"
echo [%DATE% %TIME%] Ende   %N%  - Exit %E%
>>"%UE%" echo [%DATE% %TIME%] Ende   %N%  - Exit %E%
goto :eof
