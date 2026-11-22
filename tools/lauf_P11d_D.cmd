@echo off
rem P11d, Lauf D (Werkbuch HalligHooge Teil A, 12.12, Dateien 59, 60): Glitzern mit Smith (A24), Spiegelung aus vier Quantilen
rem (A26), Himmelslicht durch die raue Flaeche in die Saeule (A23, A27); Pruefsteine 23-25.
rem 1 Renderer bauen; 2 FlaecheCheck --glanz; 3 FlaecheCheck --wellen; 4 SaeuleCheck --leiter (Stufen 1-3); 5 BildCheck;
rem 6 BuehneCheck; 7 ReferenzCheck 256 Pfade, Ansichten 1, 3, 4, 5 auf _p8c (wie P10d Lauf D); 8 HalligHooge bauen,
rem ImportPruefung; 9 Anwendung 40 s.
rem Ausgaben: HalligHooge\docs\status\Status_Phase11dD_*.txt; Atteste Renderer\docs\status\P11d_Glanz.md,
rem P10_FlaecheWellen.md, P10_SaeuleLeiter.md, P6_Bild.md, P11d_ReferenzD.md (mit .png).
rem Erwartete Dauer: rund 55 Minuten (ReferenzCheck rund 40 min).
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "A8=D:\Daten\HalligHooge\_p8c\archiv"
set "A8R=D:\Daten\HalligHooge\_p8c\archiv_ruhig"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase11dD_.txt"
set "L1=%ST%\Status_Phase11dD_1.txt"
set "L2=%ST%\Status_Phase11dD_2.txt"
set "L3=%ST%\Status_Phase11dD_3.txt"
set "L4=%ST%\Status_Phase11dD_4.txt"
set "L5=%ST%\Status_Phase11dD_5.txt"
set "L6=%ST%\Status_Phase11dD_6.txt"
set "L7=%ST%\Status_Phase11dD_7.txt"
set "L8=%ST%\Status_Phase11dD_8.txt"
set "L9=%ST%\Status_Phase11dD_9.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P11d D, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase11dD_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase11dD_2: FlaecheCheck --glanz (A24)
>"%L3%" echo Status_Phase11dD_3: FlaecheCheck --wellen (A26)
>"%L4%" echo Status_Phase11dD_4: SaeuleCheck --leiter (A27, Stufen 1-3)
>"%L5%" echo Status_Phase11dD_5: BildCheck
>"%L6%" echo Status_Phase11dD_6: BuehneCheck
>"%L7%" echo Status_Phase11dD_7: ReferenzCheck, Ansichten 1, 3, 4, 5 (256 Pfade)
>"%L8%" echo Status_Phase11dD_8: HalligHooge bauen, ImportPruefung
>"%L9%" echo Status_Phase11dD_9: Anwendung 40 s

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

set "N=2 FlaecheCheck --glanz (A24)"
set "L=%L2%"
set "C=%R% com.dan.render.reference.demo.FlaecheCheck --glanz"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 FlaecheCheck --wellen (A26)"
set "L=%L3%"
set "C=%R% com.dan.render.reference.demo.FlaecheCheck --wellen"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 SaeuleCheck --leiter (A27, Stufen 1-3)"
set "L=%L4%"
set "C=java %U% -Dp10d.stufen=3 --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.render.reference.demo.SaeuleCheck --leiter --pfade 2000000"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 BildCheck"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 BuehneCheck"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=7 ReferenzCheck, Ansichten 1, 3, 4, 5"
set "L=%L7%"
set "C=%R% com.dan.lauf.demo.ReferenzCheck --gebiet %G% --archiv %A8% --archiv-ruhig %A8R% --pfade 256 --nur 1345 --name P11d_ReferenzD"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=8 Renderer.jar und FStyle.jar ins Projekt, HalligHooge bauen (clean jar)"
set "L=%L8%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L8%" 2>&1
copy /y dist\lib\FStyle.jar "%HH%\lib\FStyle.jar" >>"%L8%" 2>&1
pushd "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" (
  popd
  goto :abbruch
)
set "N=8b ImportPruefung"
set "JC=java"
if exist "%JAVA_HOME%\bin\javac.exe" set "JC="%JAVA_HOME%\bin\java.exe""
set "C=%JC% %U% tools\ImportPruefung.java src lib\FStyle.jar"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=9 Anwendung: Ruhiger Tag, 40 s"
set "L=%L9%"
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
