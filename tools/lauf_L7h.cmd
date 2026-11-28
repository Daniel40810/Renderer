@echo off
rem Seegang Lauf L7h (Seegang Werkbuch, Datei 107): S9b-5 - wer macht das laufende Bild 12 ms teurer?
rem L7f und L7g: das laufende Bild springt in anleger_see bei Szenenzeit rund +385 s nach dem Vorlauf von 38 auf 50 ms; alle
rem Durchgaenge gleichmaessig langsamer, kein Bau der Flaeche, das stehende Bild bleibt 36 ms. Jetzt beginnt die Ansicht 400 s
rem spaeter; erst das stehende Bild zur jetzigen Zeit, dann je Schalter (Auflauf, Brandung, Gestalt, Bewuchs, Kimmung,
rem Spiegelung, Kraeuseln, Flotte) zweimal 60 Bilder an und aus.
rem 1 Renderer bauen; 2 WaermeCheck --schalter --versatz 400 (anleger_see).
rem Ausgaben: HalligHooge\docs\status\Status_L7h_*.txt, S9b_Schalter_L7h.md.
rem Erwartete Dauer: rund 10 min, hoechstens 30 min (wenn der Sprung erst nach 15 min Laufzeit kommt).
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
set "UE=%ST%\Status_L7h_.txt"
set "L1=%ST%\Status_L7h_1.txt"
set "L2=%ST%\Status_L7h_2.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf L7h (S9b-5 Karte unter Dauerlast), gestartet %DATE% %TIME%
>"%L1%" echo Status_L7h_1: Renderer bauen
>"%L2%" echo Status_L7h_2: WaermeCheck

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch
copy /y "%RE%\dist\Renderer.jar" "%SH%\lib\Renderer.jar" >>"%L1%" 2>&1
copy /y "%RE%\dist\Renderer.jar" "%HH%\lib\Renderer.jar" >>"%L1%" 2>&1
set "N=2 WaermeCheck --schalter --versatz 400 (anleger_see)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.WaermeCheck --szenen %SZ% --schalter --versatz 400"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"
copy /y "%RE%\docs\status\S9b_Waerme.md" "%ST%\S9b_Schalter_L7h.md" >>"%L2%" 2>&1

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
