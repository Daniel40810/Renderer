@echo off
rem P13d, Lauf D (Werkbuch HalligHooge Teil A, 12.14, Dateien 106 und 107; F165-F169 A): Abweichungen und Leistung.
rem 1 Renderer bauen; 2 BildzeitCheck (Kosten im Bild abwechselnd und wiederholt, A41/A30; Nachthimmel, A36);
rem 3 Wind des DWD historisch laden (Helgoland, List, 2010-2019; braucht das Netz); 4 Aufbereitung, Schritt wind;
rem 5 XaverCheck, Teil wind (ERA5 gegen DWD am 5./6.12.2013, A31); 6 SeegangKostenCheck (Seegang je Teil, A33).
rem Ausgaben: HalligHooge\docs\status\Status_Phase13dD_*.txt; Atteste Renderer\docs\status\P13d_Bild.md, P13d_Wind.md,
rem P13d_Seegang.md; Bilder Renderer\docs\status\P13d_Nacht.png und P13d_Nacht_1 ... 8.png; Reihen P13d_Wind.csv.
rem Erwartete Dauer: rund 35-40 min (Bau 3, Bild 10-12, Abruf 2-5, Wind 2, Seegang 12-15).
rem Anhalten zwischen zwei Schritten: eine Datei STOPP.txt neben dieses Skript legen.
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0.."

set "HH=C:\Users\Dan40810\Documents\NetBeansProjects\HalligHooge"
set "ST=%HH%\docs\status"
set "SZ=%HH%\src\szenen\Hooge.szenen"
set "G=D:\Daten\HalligHooge\gebiet"
set "U=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "R=java %U% --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar"
set "ANT=ant"
where ant >nul 2>nul || set "ANT=C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat"
set "PY=python"
where python >nul 2>nul || set "PY=py"

set "UE=%ST%\Status_Phase13dD_.txt"
set "L1=%ST%\Status_Phase13dD_1.txt"
set "L2=%ST%\Status_Phase13dD_2.txt"
set "L3=%ST%\Status_Phase13dD_3.txt"
set "L4=%ST%\Status_Phase13dD_4.txt"
set "E=0"
set "EF=0"

for /f %%t in ('powershell -NoProfile -Command "[DateTime]::Now.Ticks"') do set "T0=%%t"
>"%UE%" echo Lauf P13d D, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase13dD_1: Renderer bauen (clean jar, check-packages)
>"%L2%" echo Status_Phase13dD_2: BildzeitCheck (Kosten im Bild, Nachthimmel)
>"%L3%" echo Status_Phase13dD_3: Wind des DWD laden, Aufbereitung wind, XaverCheck wind
>"%L4%" echo Status_Phase13dD_4: SeegangKostenCheck

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

set "N=2 BildzeitCheck (Pruefsteine 3-7, 9; Bilder der Nacht)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.BildzeitCheck --szenen %SZ% --teil zeiten,nacht"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 Wind des DWD historisch laden (Kartenwerkzeug, dwd_wind)"
set "L=%L3%"
pushd "%HH%"
set "C=%PY% -u tools\kartenmaterial\kartenmaterial.py laden --quellen dwd_wind --ziel D:\Daten\HalligHooge"
call :lauf
popd
if not "%E%"=="0" set "EF=1"
set "E=0"
dir D:\Daten\HalligHooge\wetter\dwd_wind >>"%L3%" 2>&1

set "N=4 Aufbereitung, Schritt wind"
set "L=%L3%"
set "C=java %U% -Xmx8g -cp dist\Renderer.jar com.dan.aufbereitung.Aufbereitung ..\HalligHooge\gebiet\Hooge.rezept --schritte wind"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=5 XaverCheck, Teil wind (Pruefstein 8)"
set "L=%L3%"
set "C=%R% com.dan.lauf.demo.XaverCheck --teil wind --gebiet %G%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=6 SeegangKostenCheck (Pruefsteine 1, 2)"
set "L=%L4%"
set "C=%R% com.dan.lauf.demo.SeegangKostenCheck --gebiet %G% --start 2013-12-05T06:00:00Z --vor 4 --dauer 2 --gitter "500,100;1000,200""
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
