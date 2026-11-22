@echo off
rem P10d, Lauf D (Werkbuch HalligHooge Teil A, 12.11, Datei 47): F106 B - der Himmel wieder mit Albedo 0,23 fest (Mischung
rem nach dem Anteil des Wassers aus); die Mischung je Kanal (F105 A) bleibt im Code und wird in AirCheck geprueft.
rem 1 Renderer bauen (ant clean jar, mit check-packages); 2 AirCheck (Teil 6 je Kanal); 3 BildCheck (Bilder/s); 4 BuehneCheck;
rem 5 ReferenzCheck mit 256 Pfaden, Ansichten 1, 3, 4, 5 auf _p8c (wie Lauf C), mit Pruefstein 19 (Kugel gegen Szene).
rem Ausgaben: HalligHooge\docs\status\Status_Phase10dD_*.txt (UTF-8), Uebersicht in Status_Phase10dD_.txt.
rem Atteste: Renderer\docs\status\P6_Himmel.md, P6_Bild.md, P10d_ReferenzD.md (mit .png).
rem Erwartete Dauer: rund 85 Minuten (Schritte 1-4 rund 7 min, ReferenzCheck rund 75 min [S]).
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "A6=D:\Daten\HalligHooge\_p6\archiv"
set "A8=D:\Daten\HalligHooge\_p8c\archiv"
set "A8R=D:\Daten\HalligHooge\_p8c\archiv_ruhig"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "SZ=%HH%\src\szenen\Hooge.szenen"

set "UE=%ST%\Status_Phase10dD_.txt"
set "L1=%ST%\Status_Phase10dD_1.txt"
set "L2=%ST%\Status_Phase10dD_2.txt"
set "L3=%ST%\Status_Phase10dD_3.txt"
set "L4=%ST%\Status_Phase10dD_4.txt"
set "L5=%ST%\Status_Phase10dD_5.txt"

>"%UE%" echo Lauf P10d D, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase10dD_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase10dD_2: AirCheck (Teil 6 je Kanal)
>"%L3%" echo Status_Phase10dD_3: BildCheck
>"%L4%" echo Status_Phase10dD_4: BuehneCheck
>"%L5%" echo Status_Phase10dD_5: ReferenzCheck, Ansichten 1, 3, 4, 5 (256 Pfade)

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 AirCheck (Teil 6 je Kanal)"
set "L=%L2%"
set "C=%R% com.dan.render.air.demo.AirCheck"
call :lauf

set "N=3 BildCheck"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.BildCheck --gebiet %G% --archiv %A6% --nur 2,3"
call :lauf

set "N=4 BuehneCheck"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.BuehneCheck --szenen %SZ%"
call :lauf

set "N=5 ReferenzCheck, Ansichten 1, 3, 4, 5"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.ReferenzCheck --gebiet %G% --archiv %A8% --archiv-ruhig %A8R% --pfade 256 --nur 1345 --name P10d_ReferenzD"
call :lauf

>>"%UE%" echo.
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
