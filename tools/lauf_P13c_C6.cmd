@echo off
rem P13c, Lauf C6 (Werkbuch HalligHooge Teil A, 12.14, Datei 105; F164 A): Feinstruktur des Schaums nie aufgeloest
rem (weiche Raender nah am Auge); dazu die Diagnose der Westspitze noch einmal.
rem 1 Renderer bauen; 2 BrandungCheck (Kern); 3 BrandungBildCheck (Pruefsteine 15, 18, 7, 9, Bilder); 4 Diagnose.
rem Ausgaben: HalligHooge\docs\status\Status_Phase13cC6_*.txt; Atteste Renderer\docs\status\P13c_Brandung.md,
rem P13c_BrandungBild.md; Bilder Renderer\docs\status\P13c_Brandung_Bild.png und P13c_Brandung_1 ... 6.png.
rem Erwartete Dauer: rund 5 min.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "G=D:\Daten\HalligHooge\gebiet"
set "AX=D:\Daten\HalligHooge\_p13\archiv_xaver"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase13cC6_.txt"
set "L1=%ST%\Status_Phase13cC6_1.txt"
set "L2=%ST%\Status_Phase13cC6_2.txt"
set "L3=%ST%\Status_Phase13cC6_3.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P13c C6, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase13cC6_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase13cC6_2: BrandungCheck (Kern)
>"%L3%" echo Status_Phase13cC6_3: BrandungBildCheck (Karte)

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

set "N=2 BrandungCheck (Kern)"
set "L=%L2%"
set "C=%R% com.dan.render.brandung.demo.BrandungCheck --gebiet %G% --archiv %AX%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 BrandungBildCheck (Pruefsteine 15, 18, 7, 9, Bilder)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.BrandungBildCheck --szenen %SZ% --teil probe,bitgleich,bild"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 Diagnose A35 (Westspitze, je ein Teil aus)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.BrandungBildCheck --szenen %SZ% --teil diagnose"
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
