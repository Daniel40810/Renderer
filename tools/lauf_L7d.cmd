@echo off
rem Seegang Lauf L7d (Seegang Werkbuch, Datei 107): S9b-5 - warum misst die Abnahme in Hoch 45-60 ms je Bild, die
rem BildzeitDiagnose in denselben Ansichten 25-45 ms (L7c)? Die Flotte ist es nicht (A gegen B gegen C in L7c).
rem SeegangAbnahme misst jetzt je Stufe vorher das stehende Bild (Thermometer), zaehlt Bilder mit laufendem Bau der Flaeche
rem und stockende Bilder, und gibt die Heaps der Karte (Belegung gegen Budget) aus. Ohne Warmlauf (in L7c gleich wie mit).
rem 1 Renderer, Steuerhaus, HalligHooge bauen; 2 SeegangAbnahme ohne Warmlauf.
rem Ausgaben: HalligHooge\docs\status\Status_L7d_*.txt, S9d_Abnahme.md.
rem Erwartete Dauer: rund 15 min, hoechstens 25 min [geschaetzt: Bau 2, Abnahme 12].
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
set "UE=%ST%\Status_L7d_.txt"
set "L1=%ST%\Status_L7d_1.txt"
set "L2=%ST%\Status_L7d_2.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L7d (S9b-5 Abnahme benennen), gestartet %DATE% %TIME%
>"%L1%" echo Status_L7d_1: Renderer, Steuerhaus, HalligHooge bauen
>"%L2%" echo Status_L7d_2: SeegangAbnahme ohne Warmlauf (Thermometer, Bau)

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

set "N=2 SeegangAbnahme ohne Warmlauf, mit Thermometer und Bau je Stufe"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SeegangAbnahme --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9b_Abnahme.md" "%ST%\S9d_Abnahme.md" >>"%L2%" 2>&1

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
