@echo off
rem Seegang Nachlauf Xaver mit Quellen je 10 s (Seegang Werkbuch, Datei 21; SF30 A).
rem Rechnet den Nachlauf des Seegangs zu Xaver neu und ersetzt seegang_G2w und seegang_G3w in daten\_p13\archiv_xaver.
rem Ohne Bau: nimmt dist\Renderer.jar aus Lauf L2h (Quellen je 10 s). Waehrend dieser Lauf rechnet, keinen anderen Lauf
rem starten, der den Renderer baut (die Datei Renderer.jar ist dann belegt).
rem Erwartete Dauer: rund 1 h 40 min [geschaetzt: L1 63 min mal 1,53 wie Sturm und ruhiger Tag in L2h].
rem Ausgaben: HalligHooge\docs\status\Status_S3c5X_*.txt; Attest Renderer\docs\status\S3c5_Nachlauf_Xaver.md.
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
if not exist "dist\Renderer.jar" (
  echo dist\Renderer.jar fehlt - zuerst Lauf L2h
  timeout /t 60 >nul
  exit /b 1
)
set "UE=%ST%\Status_S3c5X_.txt"
set "L1=%ST%\Status_S3c5X_1.txt"
>"%UE%" echo Nachlauf Xaver mit Quellen je 10 s, gestartet %DATE% %TIME%
>"%L1%" echo Status_S3c5X_1: Nachlauf des Seegangs, Xaver, Quellen je 10 s
set "C=%R% com.dan.lauf.demo.SeegangNachlaufLauf --gebiet %G% --archive %D%\_p13\archiv_xaver --attest S3c5_Nachlauf_Xaver"
echo [%DATE% %TIME%] Start  Nachlauf Xaver
>>"%UE%" echo [%DATE% %TIME%] Start  Nachlauf Xaver
>>"%L1%" echo.
>>"%L1%" echo ^>%C%
%C% >>"%L1%" 2>&1
set "E=%ERRORLEVEL%"
echo [%DATE% %TIME%] Ende   Nachlauf Xaver  - Exit %E%
>>"%UE%" echo [%DATE% %TIME%] Ende   Nachlauf Xaver  - Exit %E%
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
timeout /t 20 >nul
