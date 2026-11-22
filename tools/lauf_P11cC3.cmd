@echo off
rem P11c3, Lauf C3 (Werkbuch HalligHooge Teil A, 12.12, Datei 58): Schnitt, Bilanz, Klappe Messblick (Pruefsteine 20, 21).
rem 1 Renderer bauen; 2 AnalysisCheck; 3 MessblickCheck; 4 MessblickCheck mit Validierung; 5 BuehneCheck; 6 HalligHooge bauen, ImportPruefung; 7 Anwendung 40 s mit Messblick und Schnitt.
rem Erwartete Dauer: rund 12 Minuten.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "RV=java %U% -Dcore.gpu.validierung=true --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase11cC3_.txt"
set "L1=%ST%\Status_Phase11cC3_1.txt"
set "L2=%ST%\Status_Phase11cC3_2.txt"
set "L3=%ST%\Status_Phase11cC3_3.txt"
set "L4=%ST%\Status_Phase11cC3_4.txt"
set "L5=%ST%\Status_Phase11cC3_5.txt"
set "L6=%ST%\Status_Phase11cC3_6.txt"
set "L7=%ST%\Status_Phase11cC3_7.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P11c3 C3, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase11cC3_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase11cC3_2: AnalysisCheck
>"%L3%" echo Status_Phase11cC3_3: MessblickCheck
>"%L4%" echo Status_Phase11cC3_4: MessblickCheck mit Validierung
>"%L5%" echo Status_Phase11cC3_5: BuehneCheck
>"%L6%" echo Status_Phase11cC3_6: HalligHooge bauen, ImportPruefung
>"%L7%" echo Status_Phase11cC3_7: Anwendung 40 s mit Messblick und Schnitt

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

set "N=2 AnalysisCheck"
set "L=%L2%"
set "C=%R% com.dan.render.analysis.demo.AnalysisCheck"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 MessblickCheck"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.MessblickCheck --szenen %SZ% --attest P11c3_Messblick"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 MessblickCheck mit Validierung"
set "L=%L4%"
set "C=%RV% com.dan.lauf.demo.MessblickCheck --szenen %SZ% --attest P11c3_Messblick_Validierung"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 BuehneCheck"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 Renderer.jar und FStyle.jar ins Projekt, HalligHooge bauen (clean jar)"
set "L=%L6%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L6%" 2>&1
copy /y dist\lib\FStyle.jar "%HH%\lib\FStyle.jar" >>"%L6%" 2>&1
pushd "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" (
  popd
  goto :abbruch
)
set "N=6b ImportPruefung"
set "JC=java"
if exist "%JAVA_HOME%\bin\javac.exe" set "JC="%JAVA_HOME%\bin\java.exe""
set "C=%JC% %U% tools\ImportPruefung.java src lib\FStyle.jar"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=7 Anwendung: Ruhiger Tag mit Sediment, 40 s, Messblick und Schnitt"
set "L=%L7%"
set "C=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -jar dist\HalligHooge.jar --szenen src\szenen\Hooge.szenen --szenario ruhigsed --sekunden 40 --messblick --schnitt 469500 6043610 470700 6043610"
call :lauf
if not "%E%"=="0" set "EF=1"
popd

>>"%UE%" echo.
if not "%EF%"=="0" >>"%UE%" echo Mindestens ein Schritt mit Exit ungleich 0 - siehe die Statusdateien und Atteste
>>"%UE%" echo FERTIG %DATE% %TIME%
echo.
echo Fertig. Uebersicht: %UE%
echo Die Anwendung startet jetzt mit: %HH%\HalligHooge.cmd (Messblick im Bedienfeld)
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
