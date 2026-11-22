@echo off
rem P12c, Lauf C3 (Werkbuch HalligHooge Teil A, 12.13, Dateien 81, 82): Referenz und Foto mit Haeusern, Koerpern,
rem Gestalt und Gehoelzen (F138, F141-F143 A).
rem 1 Renderer bauen; 2 ReferenceCheck (Kern, schnell, ohne Attest); 3 ReferenzBauCheck (Pruefsteine 8, 19, 9, 10; drei
rem Fotos zu je 8 min); 4 ReferenzCheck Ansicht 1 mit Gestalt aus (wie P11d); 5 HalligHooge bauen, ImportPruefung;
rem 6 Anwendung 40 s.
rem Ausgaben: HalligHooge\docs\status\Status_Phase12cC3_*.txt; Atteste Renderer\docs\status\P12c_ReferenzBau.md,
rem P12c_ReferenzD.md; Bilder Renderer\docs\status\P12c_Foto*.png.
rem Erwartete Dauer: rund 45 Minuten.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "A8=D:\Daten\HalligHooge\_p8c\archiv"
set "A8R=D:\Daten\HalligHooge\_p8c\archiv_ruhig"
set "G=D:\Daten\HalligHooge\gebiet"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase12cC3_.txt"
set "L1=%ST%\Status_Phase12cC3_1.txt"
set "L2=%ST%\Status_Phase12cC3_2.txt"
set "L3=%ST%\Status_Phase12cC3_3.txt"
set "L4=%ST%\Status_Phase12cC3_4.txt"
set "L5=%ST%\Status_Phase12cC3_5.txt"
set "L6=%ST%\Status_Phase12cC3_6.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P12c C3, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase12cC3_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase12cC3_2: ReferenceCheck (Kern, schnell)
>"%L3%" echo Status_Phase12cC3_3: ReferenzBauCheck (Pruefsteine 8, 19, 9, 10)
>"%L4%" echo Status_Phase12cC3_4: ReferenzCheck Ansicht 1, Gestalt aus
>"%L5%" echo Status_Phase12cC3_5: HalligHooge bauen, ImportPruefung
>"%L6%" echo Status_Phase12cC3_6: Anwendung: Ruhiger Tag, 40 s

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

set "N=2 ReferenceCheck (Kern, schnell)"
set "L=%L2%"
set "C=%R% com.dan.render.reference.demo.ReferenceCheck --schnell --ohne-attest"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 ReferenzBauCheck (Pruefsteine 8, 19, 9, 10)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.ReferenzBauCheck --szenen %SZ% --sekunden 480"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 ReferenzCheck Ansicht 1, Gestalt aus"
set "L=%L4%"
set "C=java %U% -Dlauf.gestalt=aus --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.ReferenzCheck --gebiet %G% --archiv %A8% --archiv-ruhig %A8R% --pfade 256 --nur 1 --name P12c_ReferenzD"
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

set "N=6 Anwendung: Ruhiger Tag, 40 s"
set "L=%L6%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen src\szenen\Hooge.szenen --szenario ruhig --sekunden 40"
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
