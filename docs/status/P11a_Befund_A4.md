# P11a – Befund zur Geschwindigkeit der Referenz Stufe 2

## Teil 1: Kernel im Treiber

Gerät: AMD Radeon(TM) Graphics; VK_AMD_shader_info ja, VK_KHR_pipeline_executable_properties ja

### /com/dan/render/reference/gpu/pfad_ebenen.comp

SPIR-V 172452 Byte; Pipeline 0 in 7,8 s
AMD: VGPR 256 von 1024 (verfügbar 256), SGPR 103 von 2048, Scratch 10800 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 2662156 Byte, 45175 Zeilen, Zugriffe auf Scratch rund 11109; Datei docs\status\P11a_Befund_pfad_ebenen_comp.isa.txt
KHR: numUsedVgprs 256; numUsedSgprs 103; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 10800; 

### /com/dan/render/reference/gpu/tafeln.comp

SPIR-V 13228 Byte; Pipeline 0 in 0,1 s
AMD: VGPR 12 von 1024 (verfügbar 256), SGPR 33 von 2048, Scratch 0 Byte je Faden, LDS 0 Byte, Wellen je Recheneinheit 64 (Ergebnis 0)
Maschinencode 34377 Byte, 644 Zeilen, Zugriffe auf Scratch rund 15; Datei docs\status\P11a_Befund_tafeln_comp.isa.txt
KHR: numUsedVgprs 12; numUsedSgprs 33; ldsSizePerLocalWorkGroup 65536; ldsUsageSizeInBytes 0; scratchMemUsageInBytes 0; 

## Teil 2: einzelne Aufrufe (ms auf der Karte, je zweimal)

- Wasserflug (2 m, ohne Pfadverfolger): 1024 × 16: 19,9 / 4,5 ms (3,61e+06 Pfade/s); 16384 × 16: 38,2 / 37,8 ms (6,93e+06 Pfade/s); 65536 × 16: 147,7 / 146,4 ms (7,16e+06 Pfade/s); 
- Himmel über der Kugel, Sonne 40°: 64 × 1: 8,3 / 6,9 ms (9,30e+03 Pfade/s); 256 × 1: 10,9 / 11,3 ms (2,26e+04 Pfade/s); 1024 × 1: 19,9 / 21,7 ms (4,71e+04 Pfade/s); 4096 × 1: 77,3 / 75,2 ms (5,45e+04 Pfade/s); 16384 × 1: 271,5 / 274,9 ms (5,96e+04 Pfade/s); 65536 × 1: 1069,4 / 1068,4 ms (6,13e+04 Pfade/s); abgebrochen (über 400 ms)
- Ofentest, glattes Wasser über Grund: 64 × 1: 344,8 / 288,2 ms (222 Pfade/s); 256 × 1: 449,7 / 459,0 ms (558 Pfade/s); abgebrochen (über 400 ms)
- Ofentest ohne Wasser (Luft und Boden): 64 × 1: 49,2 / 3,0 ms (2,10e+04 Pfade/s); 256 × 1: 52,1 / 4,4 ms (5,79e+04 Pfade/s); 1024 × 1: 55,7 / 10,9 ms (9,40e+04 Pfade/s); 4096 × 1: 82,3 / 198,1 ms (2,07e+04 Pfade/s); 
