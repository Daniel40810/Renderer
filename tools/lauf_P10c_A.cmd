@echo off
rem P10c, Lauf A (Werkbuch HalligHooge Teil A, 12.11, Datei 41): Regen in atmos (F97 A) und Flugsand (aeolian) im Nachlauf.
rem 1 Renderer bauen (ant clean jar, mit check-packages); 2 CoreCheck; 3 AtmosCheck (mit Teil 15 Regen); 4 AeolianCheck
rem (Pruefsteine 1-9); 5 AeolianLaufCheck zu archiv_m1b (Pruefsteine 11-15): Lauf 1 ohne Regen nach archiv_m1b_ohneRegen
rem (rund 2 GB, bleibt liegen), Lauf 2 mit Regen ins Archiv (atmos_G3, luft_G3s neu, aeolian_G3 dazu); 6 AtmosLaufCheck zu
rem archiv_ruhig und archiv_m1b (atmos mit Regen neu abgelegt); 7 KimmungKarteCheck (Kimmung mit der neuen Luft).
rem Ausgaben: HalligHooge\docs\status\Status_Phase10cA_*.txt (UTF-8), Uebersicht in Status_Phase10cA_.txt.
rem Atteste: Renderer\docs\status\P10a_Atmos.md, P10c_Aeolian.md, P10c_AeolianLauf.md, P10c_Atmos.md, P10c_KimmungKarte.md (mit .png).
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "G=D:\Daten\HalligHooge\gebiet"
set "AK=D:\Daten\HalligHooge\_p9c\archiv_m1b"
set "AR=D:\Daten\HalligHooge\_p9b\archiv_ruhig"
set "ZEIT=2026-10-18T08:00:00Z"
set "BLICK=462600,6046900,4,325,0,5.6"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx12g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"

set "UE=%ST%\Status_Phase10cA_.txt"
set "L1=%ST%\Status_Phase10cA_1.txt"
set "L2=%ST%\Status_Phase10cA_2.txt"
set "L3=%ST%\Status_Phase10cA_3.txt"
set "L4=%ST%\Status_Phase10cA_4.txt"
set "L5=%ST%\Status_Phase10cA_5.txt"
set "L6=%ST%\Status_Phase10cA_6.txt"
set "L7=%ST%\Status_Phase10cA_7.txt"

>"%UE%" echo Lauf P10c A, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase10cA_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase10cA_2: CoreCheck
>"%L3%" echo Status_Phase10cA_3: AtmosCheck
>"%L4%" echo Status_Phase10cA_4: AeolianCheck
>"%L5%" echo Status_Phase10cA_5: AeolianLaufCheck (Nachlauf zu archiv_m1b, ohne und mit Regen)
>"%L6%" echo Status_Phase10cA_6: AtmosLaufCheck (atmos mit Regen, archiv_ruhig und archiv_m1b)
>"%L7%" echo Status_Phase10cA_7: KimmungKarteCheck (Karte, Gebiet)

set "N=1 Renderer bauen (clean jar, check-packages)"
set "L=%L1%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=2 CoreCheck"
set "L=%L2%"
set "C=%R% com.dan.core.demo.CoreCheck"
call :lauf

set "N=3 AtmosCheck"
set "L=%L3%"
set "C=%R% com.dan.physics.atmos.demo.AtmosCheck"
call :lauf

set "N=4 AeolianCheck"
set "L=%L4%"
set "C=%R% com.dan.physics.aeolian.demo.AeolianCheck"
call :lauf

set "N=5 AeolianLaufCheck (Nachlauf zu archiv_m1b, ohne und mit Regen)"
set "L=%L5%"
set "C=%R% com.dan.lauf.demo.AeolianLaufCheck --gebiet %G% --archiv %AK% --name P10c_AeolianLauf"
call :lauf
if not "%E%"=="0" goto :abbruch

set "N=6 AtmosLaufCheck (atmos mit Regen, archiv_ruhig und archiv_m1b)"
set "L=%L6%"
set "C=%R% com.dan.lauf.demo.AtmosLaufCheck --gebiet %G% --archiv %AR%,%AK% --name P10c_Atmos"
call :lauf

set "N=7 KimmungKarteCheck (Karte, Gebiet)"
set "L=%L7%"
set "C=%R% com.dan.lauf.demo.KimmungKarteCheck --gebiet %G% --archiv %AK% --zeit %ZEIT% --blick %BLICK% --name P10c_KimmungKarte"
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
