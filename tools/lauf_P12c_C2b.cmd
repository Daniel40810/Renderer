@echo off
rem P12c, Lauf C2b (Werkbuch HalligHooge Teil A, 12.13, Datei 79): Rest von Lauf C2. Der Renderer bleibt wie in C2.
rem 1 GestaltLaufCheck mit Bewuchs aus (Pruefstein 15 wie P12b); 2 GestaltLaufCheck mit Bewuchs (Streuung);
rem 3 HalligHooge bauen (Schalter zusammengefasst, Umfang), ImportPruefung; 4 Anwendung 40 s.
rem Ausgaben: HalligHooge\docs\status\Status_Phase12cC2b_*.txt; Attest Renderer\docs\status\P12b_GestaltLauf.md (je Schritt
rem in der Statusdatei).
rem Erwartete Dauer: rund 2 Minuten.
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

set "UE=%ST%\Status_Phase12cC2b_.txt"
set "L1=%ST%\Status_Phase12cC2b_1.txt"
set "L2=%ST%\Status_Phase12cC2b_2.txt"
set "L3=%ST%\Status_Phase12cC2b_3.txt"
set "L4=%ST%\Status_Phase12cC2b_4.txt"
set "E=0"
set "EF=0"

>"%UE%" echo Lauf P12c C2b, gestartet %DATE% %TIME%
>"%L1%" echo Status_Phase12cC2b_1: GestaltLaufCheck mit Bewuchs aus (Pruefstein 15 wie P12b)
>"%L2%" echo Status_Phase12cC2b_2: GestaltLaufCheck mit Bewuchs (Streuung)
>"%L3%" echo Status_Phase12cC2b_3: HalligHooge bauen, ImportPruefung
>"%L4%" echo Status_Phase12cC2b_4: Anwendung: Ruhiger Tag, 40 s

set "N=1 GestaltLaufCheck mit Bewuchs aus (Pruefstein 15 wie P12b)"
set "L=%L1%"
set "C=java %U% -Dlauf.bewuchs=aus --enable-native-access=ALL-UNNAMED -Xmx16g -cp dist\Renderer.jar com.dan.lauf.demo.GestaltLaufCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=2 GestaltLaufCheck mit Bewuchs (Streuung)"
set "L=%L2%"
set "C=%R% com.dan.lauf.demo.GestaltLaufCheck --szenen %SZ%"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=3 Renderer.jar und FStyle.jar ins Projekt, HalligHooge bauen (clean jar)"
set "L=%L3%"
copy /y dist\Renderer.jar "%HH%\lib\Renderer.jar" >>"%L3%" 2>&1
copy /y dist\lib\FStyle.jar "%HH%\lib\FStyle.jar" >>"%L3%" 2>&1
pushd "%HH%"
set "C=call "%ANT%" -f build.xml clean jar"
call :lauf
if not "%E%"=="0" (
  popd
  goto :abbruch
)
set "N=3b ImportPruefung"
set "JC=java"
if exist "%JAVA_HOME%\bin\javac.exe" set "JC="%JAVA_HOME%\bin\java.exe""
set "C=%JC% %U% tools\ImportPruefung.java src lib\FStyle.jar"
call :lauf
if not "%E%"=="0" set "EF=1"
set "E=0"

set "N=4 Anwendung: Ruhiger Tag, 40 s"
set "L=%L4%"
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
