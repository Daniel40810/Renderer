# tools/tree – Bäume und Gräser umwandeln

Die Wandler aus dem Projekt Nature (Phase 5 in Timmendorf), seit Timmendorf Phase 19 hier neben `com.dan.tree`:

- `usd2dtree.py` – USD aus dem Procedural Vegetation Editor von Unreal (Schwarzerle, Greasewood, **seit Phase 19 Rotbuche und junge Kiefer**). Neu: Zweige, die selbst wieder Instanzen enthalten (Rotbuche: ein Holznetz und ein innerer PointInstancer mit Blättern und Knospen), werden mit ihren Matrizen eingesammelt (`proto_geometry`).
- `fbx2dtree.py`, `fbxread.py` – FBX-Bäume und Megascans-Gras mit Texturen.
- `gltf2dtree.py` – glTF.
- `dtree.py` – das gemeinsame Format (GZIP, Big Endian, siehe `TreeAsset.read`).
- `convert_all.sh` – alle Assets aus `Nature/Assets` neu (aus Nature, dort ausführen).

Braucht `pip install usd-core scipy numpy pillow`. Aufruf immer mit `python3 -I` und Ausgabe nach `src/com/dan/tree/<art>/`.

Offen: die Europäische Linde (`European+Linden-obj.rar`) – OBJ mit Laubkarten, deren Texturen (`map\EuropeanLindenLeaf.jpg`, `…_Opacity.jpg`, Rinde) nicht beiliegen; ohne sie gibt es keinen Wandler.
