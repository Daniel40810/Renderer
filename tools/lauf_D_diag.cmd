@echo off
rem Seegang Diagnose D (Seegang Werkbuch, Datei 73): zeichnet die drei Faelle des AuflaufMassstabCheck am echten Ort auf
rem (Gelaende, Saum, Querprofile an 4 Sondenzeilen, Randantrieb, Bilanz je Seite) fuer die Auswertung in der
rem Arbeitsumgebung. Keine Pruefsteine.
rem 1 Renderer bauen (clean jar); 2 AuflaufDiagnose Deich 600 s; 3 Deckwerk und Sand je 300 s.
rem Ausgaben: Renderer\docs\status\diag_L4g\<Fall>\; Status in HalligHooge\docs\status\Status_D_*.txt.
rem Erwartete Dauer: rund 15 min, hoechstens 25 min [geschaetzt].
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "P=C:\Users\Dan40810\Documents\NetBeansProjects"
set "HH=%P%\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

if not exist "%ST%" mkdir "%ST%"
set "UE=%ST%\Status_D_.txt"
set "L1=%ST%\Status_D_1.txt"
set "L2=%ST%\Status_D_2.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Diagnose D (S6b-3b), gestartet %DATE% %TIME%
>"%L1%" echo Status_D_1: Renderer bauen
>"%L2%" echo Status_D_2: AuflaufDiagnose

set "N=1 Renderer bauen (clean jar, SPIR-V, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 AuflaufDiagnose Deich 600 s"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.AuflaufDiagnose --szenen %SZ% --fall Deich --dauer 600 --hz 10"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 AuflaufDiagnose Deckwerk und Sand je 300 s"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.AuflaufDiagnose --szenen %SZ% --fall Deckwerk,Sand --dauer 300 --hz 10"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

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
