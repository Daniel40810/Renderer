@echo off
rem P11c1, Lauf C1b (Werkbuch HalligHooge Teil A, 12.12, Datei 56): Stroemung aus dem Nachlauf hydro auf G3 (F110 A, F116 A, F117 A).
rem 1 Renderer bauen; 3 StroemungLauf ueber die drei Archive von P9c (Ablage hydro_G3, Pruefsteine 15, 16); 4 BuehneCheck.
rem Erwartete Dauer: rund 55 Minuten.
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "D=D:\Daten\HalligHooge"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx24g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase11cC1b_.txt"
set "L1=%ST%\Status_Phase11cC1b_1.txt"
set "L2=%ST%\Status_Phase11cC1b_2.txt"
set "L3=%ST%\Status_Phase11cC1b_3.txt"
set "L4=%ST%\Status_Phase11cC1b_4.txt"
set "E=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P11c1 C1b, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase11cC1b_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase11cC1b_2: Probe (entfaellt in diesem Lauf)
>"%L3%" echo Status_Phase11cC1b_3: StroemungLauf
>"%L4%" echo Status_Phase11cC1b_4: BuehneCheck

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

set "N=3 StroemungLauf (3 Archive)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.StroemungLauf --gebiet %G% --archive %D%\_p9c\archiv_m1a,%D%\_p9c\archiv_m1b,%D%\_p9c\archiv_m2 --attest P11c1_C1b"
call :lauf
set "E3=%E%"
set "E=0"

set "N=4 BuehneCheck"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
call :lauf

>>"%UE%" echo.
if not "%E3%"=="0" >>"%UE%" echo StroemungLauf mit Exit %E3% - siehe %L3% und das Attest
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
