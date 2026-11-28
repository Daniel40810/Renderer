@echo off
rem Seegang Lauf L4h (Seegang Werkbuch, Datei 74): SF74-SF76 A - Front im Teilgitter (Swash 0.3: linearer Grund, ebener
rem Spiegel an der Front, Keil), SwashKarteCheck Teil 11 (Steilhang gegen EurOtop) und Teil 12 (Strand gegen Stockdon),
rem Massstab mit dem Strand als Auskunft und den Quellen des Seegangs am Rand (SF76).
rem 1 Renderer bauen (clean jar mit SPIR-V und Paketpruefung); Renderer.jar nach Steuerhaus\lib und HalligHooge\lib;
rem   Steuerhaus und HalligHooge bauen; 2a SwashCheck (Referenz); 2b SwashKarteCheck (Teile 1-12, mit Validierung);
rem   3 AuflaufCheck (mit Validierung); 4 AuflaufMassstabCheck (Strand, Deckwerk, Deich je 900 s).
rem Ausgaben: HalligHooge\docs\status\Status_L4h_*.txt; Atteste in Renderer\docs\status.
rem Erwartete Dauer: rund 30 min, hoechstens 40 min [geschaetzt].
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "P=C:\Users\Dan40810\Documents\NetBeansProjects"
set "HH=%P%\HalligHooge"
set "SH=%P%\Steuerhaus"
set "RE=%P%\Renderer"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

if not exist "%ST%" mkdir "%ST%"
set "UE=%ST%\Status_L4h_.txt"
set "L1=%ST%\Status_L4h_1.txt"
set "L2=%ST%\Status_L4h_2.txt"
set "L3=%ST%\Status_L4h_3.txt"
set "L4=%ST%\Status_L4h_4.txt"
set "V=-Dcore.gpu.validierung=true"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L4h (S6b-3c), gestartet %DATE% %TIME%
>"%L1%" echo Status_L4h_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L4h_2: SwashCheck und SwashKarteCheck
>"%L3%" echo Status_L4h_3: AuflaufCheck mit Validierung
>"%L4%" echo Status_L4h_4: AuflaufMassstabCheck

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
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
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1
pushd "%SH%"
set "N=1b Steuerhaus bauen (clean jar)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
popd
if not "%E%"=="0" goto :abbruch
pushd "%HH%"
set "N=1c HalligHooge bauen (clean jar)"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
popd
if not "%E%"=="0" goto :abbruch

set "N=2a SwashCheck (Referenz, Swash 0.3)"
set "L=%L2%"
set "C=%R% com.dan.physics.swash.demo.SwashCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=2b SwashKarteCheck (Teile 1-12; mit Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.physics.swash.demo.SwashKarteCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 AuflaufCheck (Swash 0.3; mit Validierung)"
set "L=%L3%"
set "C=%R% %V% com.dan.lauf.demo.AuflaufCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 AuflaufMassstabCheck (Strand, Deckwerk, Deich je 900 s; ohne Validierung)"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.AuflaufMassstabCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien und Atteste
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 5 >nul
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
