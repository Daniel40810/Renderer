# mitschrift_L7k.ps1 - Mitschrift der Maschine waehrend eines Laufs (Seegang Werkbuch 108, S9c-2, SF181 A).
# Liest nur, aendert nichts. Alle Takt Sekunden eine Zeile: Last, Leistung und Takt des Prozessors, Last der Karte (3D,
# alle und java), Sekunden seit der letzten Eingabe; alle 10 s die fuenf Programme mit der meisten Rechenzeit seit dem
# letzten Mal; alle 30 s Netz/Akku und Energieplan. Zaehler ueber CIM (Klassennamen sind in jeder Sprache gleich).
# Endet, wenn die Datei Stopp da ist oder nach Minuten.
param([Parameter(Mandatory = $true)][string]$Aus, [Parameter(Mandatory = $true)][string]$Stopp, [int]$Minuten = 70, [int]$Takt = 2)
$ErrorActionPreference = 'SilentlyContinue'
$inv = [Globalization.CultureInfo]::InvariantCulture
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class Leerlauf {
    [StructLayout(LayoutKind.Sequential)] struct LII { public uint cbSize; public uint dwTime; }
    [DllImport("user32.dll")] static extern bool GetLastInputInfo(ref LII p);
    public static double Sekunden() {
        LII l = new LII(); l.cbSize = (uint)Marshal.SizeOf(l);
        if (!GetLastInputInfo(ref l)) return -1;
        return unchecked((uint)Environment.TickCount - l.dwTime) / 1000.0;
    }
}
'@
$modi = @{ 'ded574b5-45a0-4f42-8737-46345c09c238' = 'Beste Leistung'; '961cc777-2547-4f9d-8174-7d86181b8a7a' = 'Beste Energieeffizienz';
           '00000000-0000-0000-0000-000000000000' = 'Ausgeglichen'; '3af9b8d9-7c97-431d-ad78-34a8bfea439f' = 'Bessere Akkuleistung' }
function Modus([string]$g) { if ($g -and $modi.ContainsKey($g.ToLower())) { return $modi[$g.ToLower()] } elseif ($g) { return $g } else { return '-' } }

'zeit;cpu_last;cpu_zeit;cpu_leistung;cpu_mhz;gpu3d_alle;gpu3d_java;leerlauf_s;netz;akku;plan;top5' | Out-File -FilePath $Aus -Encoding utf8
$ende = (Get-Date).AddMinutes($Minuten)
$alt = @{}; $n = 0; $netz = ''; $akku = ''; $plan = ''; $top = ''
while ((Get-Date) -lt $ende -and -not (Test-Path $Stopp)) {
    $t0 = Get-Date
    $p = Get-CimInstance -ClassName Win32_PerfFormattedData_Counters_ProcessorInformation -Filter "Name='_Total'" | Select-Object -First 1
    $g = @(Get-CimInstance -ClassName Win32_PerfFormattedData_GPUPerformanceCounters_GPUEngine | Where-Object { $_.Name -like '*engtype_3D' })
    $gAlle = ($g | Measure-Object -Property UtilizationPercentage -Sum).Sum
    $java = @(Get-Process -Name java, javaw | ForEach-Object { 'pid_' + $_.Id + '_' })
    $gJava = 0
    foreach ($e in $g) { foreach ($j in $java) { if ($e.Name.StartsWith($j)) { $gJava += $e.UtilizationPercentage } } }
    if ($n % 5 -eq 0) {
        $neu = @{}; $d = @()
        foreach ($q in Get-Process) {
            if ($q.CPU -eq $null) { continue }
            $k = $q.ProcessName + '#' + $q.Id
            $neu[$k] = $q.CPU
            if ($alt.ContainsKey($k)) { $d += [pscustomobject]@{ N = $q.ProcessName; S = $q.CPU - $alt[$k] } }
        }
        $alt = $neu
        $top = ($d | Sort-Object S -Descending | Select-Object -First 5 | ForEach-Object { $_.N + ':' + $_.S.ToString('0.0', $inv) }) -join ' '
    }
    if ($n % 15 -eq 0) {
        $b = Get-CimInstance -Namespace root/wmi -ClassName BatteryStatus | Select-Object -First 1
        $netz = if ($b) { if ($b.PowerOnline) { 'Netz' } else { 'Akku' } } else { 'kein Akku' }
        $w = Get-CimInstance -ClassName Win32_Battery | Select-Object -First 1
        $akku = if ($w) { [string]$w.EstimatedChargeRemaining + ' %' } else { '' }
        $ps = Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\Power\User\PowerSchemes'
        $pl = (powercfg /getactivescheme) -join ' '
        if ($pl -match '\(([^)]*)\)') { $pl = $Matches[1] }
        $plan = $pl + ' / Modus am Netz ' + (Modus $ps.ActiveOverlayAcPowerScheme) + ', am Akku ' + (Modus $ps.ActiveOverlayDcPowerScheme)
    }
    $z = [string]::Format($inv, '{0};{1:0.0};{2:0.0};{3:0.0};{4:0};{5:0.0};{6:0.0};{7:0};{8};{9};{10};{11}', $t0.ToString('HH:mm:ss'),
        [double]$p.PercentProcessorUtility, [double]$p.PercentProcessorTime, [double]$p.PercentProcessorPerformance, [double]$p.ProcessorFrequency, [double]$gAlle, [double]$gJava,
        [Leerlauf]::Sekunden(), $netz, $akku, $plan, $top)
    $z | Out-File -FilePath $Aus -Append -Encoding utf8
    $n++
    $rest = $Takt - ((Get-Date) - $t0).TotalSeconds
    if ($rest -gt 0) { Start-Sleep -Milliseconds ([int]($rest * 1000)) }
}
