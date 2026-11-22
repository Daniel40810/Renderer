@echo off
rem P13c, Lauf C (Werkbuch HalligHooge Teil A, 12.14, Datei 99; F159-F163 A): Brandung im Bild.
rem 1 Renderer bauen; 2 Aufbereitung, Schritt brandung (Kanten nach D:\Daten\HalligHooge\gebiet\brandung); 3 BrandungCheck
rem (Kern: Pruefsteine 16, 18, 8a, 17, 8c, 19); 4 BrandungBildCheck auf der Karte (Pruefsteine 15, 18, 7, 9, Bilder mit und
rem ohne Brandung); 5 HalligHooge bauen, ImportPruefung; 6 Anwendung: Xaver mit Brandung, 60 s.
rem Ausgaben: HalligHooge\docs\status\Status_Phase13cC_*.txt; Atteste Renderer\docs\status\P13c_Brandung.md,
rem P13c_BrandungBild.md; Bilder Renderer\docs\status\P13c_Brandung_Bild.png und P13c_Brandung_1 ... 6.png.
rem Erwartete Dauer: rund 25 min (Schritt 4 rund 15 min).
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "RZ=%HH%\gebiet\Hooge.rezept"
set "G=D:\Daten\HalligHooge\gebiet"
set "AX=D:\Daten\HalligHooge\_p13\archiv_xaver"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase13cC_.txt"
set "L1=%ST%\Status_Phase13cC_1.txt"
set "L2=%ST%\Status_Phase13cC_2.txt"
set "L3=%ST%\Status_Phase13cC_3.txt"
set "L4=%ST%\Status_Phase13cC_4.txt"
set "L5=%ST%\Status_Phase13cC_5.txt"
set "L6=%ST%\Status_Phase13cC_6.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P13c C, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase13cC_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase13cC_2: Aufbereitung, Schritt brandung
>"%L3%" echo Status_Phase13cC_3: BrandungCheck (Kern)
>"%L4%" echo Status_Phase13cC_4: BrandungBildCheck (Karte)
>"%L5%" echo Status_Phase13cC_5: HalligHooge bauen, ImportPruefung
>"%L6%" echo Status_Phase13cC_6: Anwendung: Xaver mit Brandung, 60 s

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

set "N=2 Aufbereitung, Schritt brandung"
set "L=%L2%"
set "C=java %U% -Xmx8g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung %RZ% --schritte brandung"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=3 BrandungCheck (Pruefsteine 16, 18, 8a, 17, 8c, 19)"
set "L=%L3%"
set "C=%R% com.dan.render.brandung.demo.BrandungCheck --gebiet %G% --archiv %AX%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 BrandungBildCheck (Pruefsteine 15, 18, 7, 9, Bilder)"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.BrandungBildCheck --szenen %SZ% --teil probe,bitgleich,bild"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

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
set "JC=java"
if exist "%JAVA_HOME%\bin\javac.exe" set "JC="%JAVA_HOME%\bin\java.exe""
set "C=%JC% %U% tools\ImportPruefung.java src lib\FStyle.jar"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 Anwendung: Xaver mit Brandung, 60 s"
set "L=%L6%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen src\szenen\Hooge.szenen --szenario xaver --sekunden 60"
call :lauf
if not "%E%"=="0" set "EF=1"
popd

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
