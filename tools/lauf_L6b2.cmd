@echo off
rem Seegang Lauf L6b2 (Seegang Werkbuch, Datei 104): S8d nachgebessert nach L6b - das Licht unter Wasser aus der Tafel der
rem Saeule wie das Wasser von oben, Tropfen nach dem Auftauchen, Pruefstein 38 an der Kante mit dem hoechsten Seegang,
rem Kosten unter Wasser ohne die ersten drei Bilder; Bild des Snellschen Fensters. Dazu aus S8e: das Regiebuch Seegang
rem (RegieCheck) und die Abnahme der Bildrate je Stufe (Pruefstein 42).
rem 1 Renderer bauen (mit SPIR-V und Paketpruefung); Renderer.jar nach Steuerhaus\lib und HalligHooge\lib; Steuerhaus und
rem   HalligHooge bauen; 2 SeegangS8cdCheck Teil 1-3 (Pruefsteine 38, 39, 41) - mit Validierung; 3 RegieCheck (Steuerhaus,
rem   alle Regiebuecher, neu: Seegang); 4 SeegangAbnahme (Bildrate je Stufe, 1920 x 1080, ohne Validierung);
rem 5 SchwimmerCheck Teil 4 ohne Validierung (Kosten der Karte und des Fadens; in L6a/L6b mit Validierung knapp ueber der Grenze).
rem Ausgaben: HalligHooge\docs\status\Status_L6b2_*.txt; Atteste Renderer\docs\status\S8cd_Seegang.md, S8e_Abnahme.md;
rem S7b4_Schwimmer_teil4.md; Bilder S8d_*.png, S8e_*.png.
rem Erwartete Dauer: rund 28 min, hoechstens 40 min [geschaetzt]. Gestartet von Claude per Explorer.
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
set "V=-Dcore.gpu.validierung=true"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

if not exist "%ST%" mkdir "%ST%"
set "UE=%ST%\Status_L6b2_.txt"
set "L1=%ST%\Status_L6b2_1.txt"
set "L2=%ST%\Status_L6b2_2.txt"
set "L3=%ST%\Status_L6b2_3.txt"
set "L4=%ST%\Status_L6b2_4.txt"
set "L5=%ST%\Status_L6b2_5.txt"
set "E=0"
set "EF=0"
for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf L6b2 (S8d nachgebessert), gestartet %DATE% %TIME%
>"%L1%" echo Status_L6b2_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L6b2_2: SeegangS8cdCheck mit Validierung
>"%L3%" echo Status_L6b2_3: RegieCheck
>"%L4%" echo Status_L6b2_4: SeegangAbnahme
>"%L5%" echo Status_L6b2_5: SchwimmerCheck Teil 4 ohne Validierung


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

set "N=2 SeegangS8cdCheck Teil 1-3 (mit Validierung)"
set "L=%L2%"
set "C=%R% %V% com.dan.lauf.demo.SeegangS8cdCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 RegieCheck (alle Regiebuecher)"
set "L=%L3%"
pushd "%HH%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx4g -cp dist\HalligHooge.jar com.dan.steuerhaus.demo.RegieCheck --manifest src\szenen\Hooge.steuerhaus"
call :lauf
popd
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 SeegangAbnahme (Bildrate je Stufe)"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.SeegangAbnahme --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 SchwimmerCheck Teil 4 (Kosten der Karte; ohne Validierung)"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.SchwimmerCheck --szenen %SZ% --teil 4"
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
