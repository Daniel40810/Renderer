@echo off
rem Seegang Lauf L7c (Seegang Werkbuch, Datei 107): S9b-0 Bildzeit benennen und S9b-4 (Mittel ohne Spiegelung, SF173 A).
rem Anlass (L7b): Die Abnahme misst in Hoch 45-50 ms je Bild, LeistungCheck in denselben Ansichten laufend 34-38 ms.
rem LeistungCheck wartet nach jedem Bild auf den Faden der Flotte, die Abnahme nicht. BildzeitDiagnose misst je Ansicht im
rem Wechsel: A Flotte nebenher (wie die Anwendung), B nach jedem Bild warten, C Flotte ruht, S stehendes Bild (Thermometer);
rem dazu Pruefstein 45/46 mit fester Belichtung (in L7b verdeckte die laufende Belichtung den Vergleich).
rem S9b-4: Mittel und Niedrig ohne Spiegelung des Gelaendes (Qualitaet.Teil.SPIEGELUNG; Steuerhaus und SeegangAbnahme).
rem 1 Renderer, Steuerhaus, HalligHooge bauen; 2 BildzeitDiagnose; 3 SeegangAbnahme warm (180 s) mit S9b-4.
rem Ausgaben: HalligHooge\docs\status\Status_L7c_*.txt, S9b_Bildzeit.md, S9c_Abnahme.md, S9c_*.png.
rem Erwartete Dauer: rund 35 min, hoechstens 50 min [geschaetzt: Bau 2, BildzeitDiagnose 15, Abnahme 15].
rem Gestartet von Claude per Explorer. Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
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
set "UE=%ST%\Status_L7c_.txt"
set "L1=%ST%\Status_L7c_1.txt"
set "L2=%ST%\Status_L7c_2.txt"
set "L3=%ST%\Status_L7c_3.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L7c (S9b-0 Bildzeit, S9b-4 Mittel ohne Spiegelung), gestartet %DATE% %TIME%
>"%L1%" echo Status_L7c_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L7c_2: BildzeitDiagnose
>"%L3%" echo Status_L7c_3: SeegangAbnahme --warm 180 (mit S9b-4)

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
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

set "N=2 BildzeitDiagnose (Flotte nebenher, warten, ruht, stehend; feste Belichtung)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.BildzeitDiagnose --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9b_Bildzeit.md" "%ST%\S9b_Bildzeit.md" >>"%L2%" 2>&1

set "N=3 SeegangAbnahme warm (180 s Warmlauf), Mittel ohne Spiegelung"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.SeegangAbnahme --szenen %SZ% --warm 180"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9b_Abnahme.md" "%ST%\S9c_Abnahme.md" >>"%L3%" 2>&1
for %%F in ("%RE%\docs\status\S9b_*_*.png") do copy /y "%%F" "%ST%\S9c_%%~nxF" >>"%L3%" 2>&1

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien
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
