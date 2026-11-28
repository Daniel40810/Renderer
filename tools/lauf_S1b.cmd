@echo off
rem Seegang S1, Lauf L1 Teil 2 (Seegang Werkbuch, Datei 2): der Nachlauf des Seegangs zum ruhigen Tag (30.10.) und zu Xaver
rem (5./6.12.2013), reine Rechnung auf der Karte, keine Sichtpruefung. Braucht Teil 1 (lauf_S1.cmd): das neue
rem dist\Renderer.jar und hydro_G3 in daten\_p13\archiv_xaver (sonst rechnet Xaver ohne Stroemung, mit Hinweis im Attest).
rem Ausgaben: HalligHooge\docs\status\Status_S1b_*.txt; Atteste Renderer\docs\status\S1_Nachlauf_Ruhig.md, S1_Nachlauf_Xaver.md.
rem Neu in den Daten: daten\_p8c\archiv_ruhig\seegang_G2w, seegang_G3w; daten\_p13\archiv_xaver\seegang_G2w, seegang_G3w.
rem Erwartete Dauer: rund 75 min (ruhiger Tag 27, Xaver 48). Der Rechner kann dabei anderes tun; die Karte ist belegt.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "D=%HH%\daten"
set "G=%D%\gebiet"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"

if not exist "%ST%" mkdir "%ST%"
set "UE=%ST%\Status_S1b_.txt"
set "L1=%ST%\Status_S1b_1.txt"
set "L2=%ST%\Status_S1b_2.txt"
set "E=0"
set "EF=0"
>"%UE%" echo Lauf S1 Teil 2 (Nachlauf des Seegangs: ruhiger Tag, Xaver), gestartet %DATE% %TIME%
>"%L1%" echo Status_S1b_1: Nachlauf des Seegangs, ruhiger Tag 30.10.
>"%L2%" echo Status_S1b_2: Nachlauf des Seegangs, Xaver

if not exist "dist\Renderer.jar" (
  >>"%UE%" echo dist\Renderer.jar fehlt - erst lauf_S1.cmd
  set "N=0 Renderer.jar fehlt"
  set "L=%UE%"
  goto :abbruch
)

set "N=1 Nachlauf des Seegangs, ruhiger Tag 30.10. (seegang_G2w, seegang_G3w)"
set "L=%L1%"
set "C=%R% com.dan.lauf.demo.SeegangNachlaufLauf --gebiet %G% --archive %D%\_p8c\archiv_ruhig --attest S1_Nachlauf_Ruhig"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=2 Nachlauf des Seegangs, Xaver (seegang_G2w, seegang_G3w)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.SeegangNachlaufLauf --gebiet %G% --archive %D%\_p13\archiv_xaver --attest S1_Nachlauf_Xaver"
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
