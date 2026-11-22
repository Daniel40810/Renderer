@echo off
rem P11b, Lauf B (Werkbuch HalligHooge Teil A, 12.12, Datei 53): Foto mit der Referenz Stufe 1 (F112 B, F113 A).
rem 1 Renderer bauen; 2 FotoCheck (Pruefsteine 9-12, je Szenario aus dem Archiv ein Foto mit 3 min); 3 BuehneCheck;
rem 4 BildCheck (Bilder/s); 5 HalligHooge bauen, ImportPruefung; 6 Anwendung 40 s. Fotos in HalligHooge\docs\fotos\FotoCheck.
rem Erwartete Dauer: rund 50 Minuten.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase11bB_.txt"
set "L1=%ST%\Status_Phase11bB_1.txt"
set "L2=%ST%\Status_Phase11bB_2.txt"
set "L3=%ST%\Status_Phase11bB_3.txt"
set "L4=%ST%\Status_Phase11bB_4.txt"
set "L5=%ST%\Status_Phase11bB_5.txt"
set "L6=%ST%\Status_Phase11bB_6.txt"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P11b B, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase11bB_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase11bB_2: FotoCheck
>"%L3%" echo Status_Phase11bB_3: BuehneCheck
>"%L4%" echo Status_Phase11bB_4: BildCheck
>"%L5%" echo Status_Phase11bB_5: HalligHooge bauen, ImportPruefung
>"%L6%" echo Status_Phase11bB_6: Anwendung 40 s

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

set "N=2 FotoCheck (Pruefsteine 9-12, rund 40 min)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.FotoCheck --szenen %SZ% --ablage %HH%\docs\fotos\FotoCheck"
call :lauf

set "N=3 BuehneCheck"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
call :lauf

set "N=4 BildCheck"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf

set "N=5 Renderer.jar und FStyle.jar ins Projekt, HalligHooge bauen (clean jar)"
set "L=%L5%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L5%" 2>&1
copy /y dist\lib\FStyle.jar "%HH%\lib\FStyle.jar" >>"%L5%" 2>&1
pushd "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" (
  popd
  goto :abbruch
)
set "N=5b ImportPruefung"
set "C=java %U% tools\ImportPruefung.java src lib\FStyle.jar"
call :lauf

set "N=6 Anwendung: ruhiger Tag, 40 s"
set "L=%L6%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen src\szenen\Hooge.szenen --szenario ruhig --sekunden 40"
call :lauf
popd

>>"%UE%" echo.
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
echo Die Anwendung startet jetzt mit: %HH%\HalligHooge.cmd (Foto im Bedienfeld)
timeout /t 20 >nul
goto :eof

:abbruch
>>"%UE%" echo.
>>"%UE%" echo ABBRUCH %DATE% %TIME% nach: %N%
echo.
echo Abbruch nach: %N%  - siehe %L%
timeout /t 60 >nul
goto :eof

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
