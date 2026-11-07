# LegendaryForge

Plugin Paper untuk **Legendary Items** (7 senjata elemen, 3 tier) + Resource Pack Java + mapping Bedrock (Geyser).
Hook: **AuraSkills** (level, stat, mana, XP boost) dan **RaceCore 2.0.0** (batasan & bonus race).

## 1. Struktur
```
LegendaryForge/
├─ pom.xml / build.gradle.kts            ← build (pilih salah satu)
├─ src/main/java/com/legendaryforge/
│  ├─ LegendaryForge.java                ← Main class (merakit semua komponen)
│  ├─ LegendaryCommand.java              ← /legendary menu|list|give|reload
│  ├─ item/   LegendaryItem, ItemManager ← parser items.yml + pembuat ItemStack (PDC id)
│  ├─ ability/ AbilityExecutor           ← 7 skill + chain lightning + soul harvest
│  │          PassiveTask                ← actionbar cooldown + passive "saat dipegang"
│  │          CooldownManager
│  ├─ listener/ ItemListener             ← klik, on-hit, on-kill, fall damage
│  ├─ hooks/  HookManager (fasad), RaceCoreHook (reflection),
│  │          AuraSkillsHook, AuraXpListener
│  └─ gui/    LegendaryMenu (Chest GUI), BedrockMenu (Cumulus Form)
├─ src/main/resources/ plugin.yml, config.yml, items.yml
├─ resourcepack/       ← Java pack (items/*.json untuk 1.21.4+, _legacy_pre_1.21.4/ untuk lama)
├─ bedrock-pack/       ← pack ikon Bedrock (zip → .mcpack)
└─ geyser/custom_mappings/legendaryforge.json
```

## 2. Kompilasi (Minecraft / Paper 26.2)
Butuh **JDK 25** untuk build **dan** menjalankan server.
```bash
mvn clean package            # hasil: target/LegendaryForge-1.0.0.jar
# atau
gradle build                 # hasil: build/libs/LegendaryForge-1.0.0.jar
```
* Paper API: `io.papermc.paper:paper-api:26.2.build.84-stable` (sudah diset di `pom.xml` & `build.gradle.kts`).
  Cek build yang lebih baru di https://repo.papermc.io/#browse/browse:maven-public:io/papermc/paper/paper-api
* `plugin.yml` memakai `api-version: '26.2'`.
* Soal Java: beberapa sumber hosting menulis "Java 21", tetapi catatan rilis plugin yang diuji langsung di Paper 26.2
  menyebut **Java 25** (server berjalan di Adoptium 25). Jadi pakai Java 25; Java 21 kemungkinan besar ditolak server.
* Versi/repo `auraskills-api-bukkit`: https://wiki.aurelium.dev/auraskills/api — pastikan ada build yang cocok untuk 26.2.

Pasang: taruh jar di `plugins/` bersama **RaceCore-2.0.0.jar**, AuraSkills, (opsional) Geyser + Floodgate.
RaceCore 2.0.0 memakai `api-version 1.21`; biasanya tetap termuat di 26.2, tetapi uji dulu.

## 3. Cara kerja & fitur
| Fitur | Di mana |
|---|---|
| Item dari YAML (CMD, nama, lore, enchant, attribute, cooldown, mana) | `items.yml` → `ItemManager.build()` |
| ID item di PDC (tidak bisa dipalsukan dengan rename di anvil) | `ItemManager.fromStack()` |
| Skill klik kanan / shift+klik kanan / klik kiri | `skill.trigger` → `ItemListener.onInteract` |
| Particle, sound modern, bossbar cast, actionbar cooldown | `AbilityExecutor`, `PassiveTask` |
| Syarat level AuraSkills | `requirements.auraskills` |
| Syarat race RaceCore | `requirements.races` (kosong = bebas) |
| Bonus race (damage & cooldown) | `race-bonus.<race>` — berlaku untuk skill DAN serangan biasa |
| AuraSkills Stat / Mana / XP boost | `stat-bonus` (wisdom = mana), `mana-cost`, `xp-boost` |

**Hook RaceCore** memakai reflection pada API yang saya temukan di jar-mu:
`RaceCore#data()` → `DataManager#raceOf(UUID)` → `Race.id`. Tidak ada compile-dependency, jadi update RaceCore tidak
memecahkan build. `Human` otomatis boleh memakai semua senjata (`racecore.human-bypass`), sesuai deskripsi di `races/default.yml`.

> ⚠ RaceCore sudah punya `WeaponSynergyListener` yang mencocokkan **nama item** ("terra breaker", "leviathan",
> "ignis", "zephyr", "luminance", "void scythe", "stormcaller"). Nama di `items.yml` sudah memuat kata-kata itu,
> sehingga bonus RaceCore **menumpuk** dengan `race-bonus` plugin ini. Kalau terasa terlalu kuat, kecilkan `race-bonus`.

## 4. Menambah item baru
Copy satu section di `items.yml`, ganti id + `model-data`, lalu `/legendary reload`.
Skill baru: tulis satu method di `AbilityExecutor`, tambah satu baris `case "ID_BARU" -> ...` di `cast()`.

## 5. Resource Pack (Java)
* `resourcepack/` di-zip (isi folder, bukan foldernya) → host (mis. GitHub release / CDN) → `server.properties`:
  `resource-pack=<url>` dan `resource-pack-sha1=<sha1 zip>`.
* `assets/minecraft/items/*.json` = format **1.21.4+** (range_dispatch pada `custom_model_data`).
* Server lebih lama: pakai `_legacy_pre_1.21.4/*.json` → salin ke `assets/minecraft/models/item/`.
* `pack.mcmeta`: `pack_format` 46 + `supported_formats` 34–999 sebagai placeholder. Sesuaikan dengan format pack versimu.
* Tekstur 64×64 pixel-art (shading, outline, glow) dibuat oleh `tools/generate_textures.py` (`pip install pillow numpy` lalu `python3 tools/generate_textures.py`). Boleh diganti PNG buatanmu sendiri dengan nama file yang sama;
  atau buat model 3D di Blockbench → simpan sebagai `models/item/<id>.json` (nama file tetap sama).
* Trident memakai ikon 2D; model 3D bawaan trident (di tangan/dilempar) butuh `special` model, tidak disertakan.

## 6. Bedrock (GeyserMC)
**Alur:**
1. Pasang Geyser + Floodgate di server.
2. Salin `geyser/custom_mappings/legendaryforge.json` → `plugins/Geyser-Spigot/custom_mappings/`
   (memetakan `material + custom_model_data` Java → item Bedrock kustom). Ini format mapping **v1**.
3. Ikon Bedrock: zip isi `bedrock-pack/` menjadi `LegendaryForge.mcpack` → taruh di `plugins/Geyser-Spigot/packs/`.
   Nama `icon` di mapping = nama key di `textures/item_texture.json`.
4. Opsional: jalankan **GeyserOptionalPack** (tekstur UI/ikon umum) + restart Geyser, cek log `Loaded X custom items`.

Catatan jujur: Geyser **tidak** mengonversi resource pack Java otomatis. Untuk model 3D/atlas otomatis gunakan
tool **Rainbow** (GeyserMC) atau buat tekstur Bedrock manual seperti `bedrock-pack/`. Format mapping Geyser terbaru
(v2, berbasis `item_model`/definisi) punya sintaks berbeda → cek https://geysermc.org/wiki/geyser/custom-items/
bila versi Geyser-mu sudah menolak v1.
Keterbatasan: Bedrock tidak mendukung particle/sound Java 100% identik (sudah dipetakan Geyser, tapi tampilan bisa sedikit beda),
dan `Glowing` serta `Blindness` tampil berbeda di Bedrock.

## 7. UI/GUI yang jalan di Java **dan** Bedrock
* **Chest GUI** (`/legendary menu`): Geyser mengubahnya jadi container Bedrock otomatis → 1 kode untuk dua platform.
  Aturan agar aman: pakai `Bukkit.createInventory` dengan ukuran 9×n, **batalkan semua klik** (`setCancelled(true)`),
  jangan andalkan drag/shift-click/offhand, item pembatas pakai `BLACK_STAINED_GLASS_PANE` bernama `" "`.
* **Form native Bedrock** (`BedrockMenu`): Floodgate + Cumulus `SimpleForm`. Dipakai otomatis jika
  `FloodgateApi.isFloodgatePlayer(uuid)`. Aturan: handler form berjalan di thread netty → bungkus aksi Bukkit dengan
  `Bukkit.getScheduler().runTask(plugin, ...)` (sudah dilakukan di kode).
  Untuk input: `CustomForm` (toggle, slider, dropdown), konfirmasi: `ModalForm`.
* Jangan memakai sign-GUI, anvil-rename GUI, atau teks `\n`+warna panjang di judul — rapuh di Bedrock.
* Warna `§`/`&` di tombol Form dibersihkan lewat `Text.plain()` karena sebagian layar Bedrock menampilkannya mentah.

## 8. Tabel item
| Tier | Item | Skill (trigger) | CD | Passive |
|---|---|---|---|---|
| SUPREME | Luminance Scepter | Judgment Ray (RMB) | 25s | Divine Grace: musuh Glowing, rekan regen |
| SUPREME | Void Scythe | Abyssal Rift (RMB) | 30s | Soul Harvester: kill → Absorption + Strength V |
| SUPREME | Stormcaller Blade | Judgment of Thor (RMB) | 15s | Overcharge: hit ke-4 = Chain Lightning |
| MID | Ignis Core Blade | Purgatory Slash (RMB) | 10s | Magma Blood: fire resistance, tebasan membakar |
| MID | Leviathan's Trident | Tsunami Prison (RMB) | 22s | Tidal Flow: Speed+Dolphin's Grace dekat air/hujan |
| MID | Terra Breaker | Earthquake Shatter (RMB) | 18s | Titan Stance: kebal knockback + Resistance |
| BASE | Zephyr Tempest Bow | Cyclone Dash (Shift+RMB) | 8s | Air Walker: tanpa fall damage + Speed II |

## 9. Hal yang perlu kamu ketahui (jujur)
* **Kode belum dikompilasi/diuji di server** — lingkungan tempat saya menulisnya tidak punya JDK compiler maupun akses
  internet ke repo Paper/AuraSkills. Saya menulisnya dengan API Paper 1.21.x yang stabil (Registry lookup, bukan enum
  yang berubah-ubah), tapi kemungkinan ada 1–2 error kecil saat build pertama (biasanya nama method/enum di versi barumu).
  Kirim log error `mvn package`-nya dan saya perbaiki.
* **Bagian AuraSkills** (`AuraSkillsHook`, `AuraXpListener`) ditulis dari ingatan terhadap API 2.x
  (`getUser`, `getSkillLevel`, `StatModifier`, `getMana/setMana`, `XpGainEvent`). Cocokkan dengan
  https://wiki.aurelium.dev/auraskills/api bila ada yang tidak ditemukan saat build.
* "Soul Fire" pada Purgatory Slash: efek visual partikel soul fire + `setFireTicks` (Bukkit tidak bisa mengganti jenis api target).
* "Rekan setim" = pemain di **scoreboard Team** yang sama. Untuk plugin party/clan, ubah `AbilityExecutor.isAlly()`.
* Mana dikurangi lewat AuraSkills (`mana-cost`). Tanpa AuraSkills, mana-cost diabaikan; cooldown tetap jalan.
* Shift+RMB pada busur membatalkan tarikan busur vanilla (event di-DENY) agar skill tidak menembakkan panah.

## 10. Build otomatis lewat GitHub Actions
File `.github/workflows/build.yml` sudah disertakan (JDK 25 + Maven).
1. Buat repo baru di GitHub, lalu upload **seluruh isi** folder `LegendaryForge/` (folder `.github` harus ikut, ia folder tersembunyi).
2. Buka tab **Actions** → workflow *Build LegendaryForge* jalan otomatis setiap push (atau klik **Run workflow**).
3. Setelah hijau ✅, buka run-nya → bagian **Artifacts** → unduh `LegendaryForge-jar` (isinya jar) dan `LegendaryForge-packs`.
4. Release permanen: buat tag, mis. `git tag v1.0.0 && git push --tags` → jar & pack otomatis muncul di tab **Releases**
   (URL file pack di Releases bisa dipakai untuk `resource-pack=` di `server.properties`).
Jika build merah, buka log langkah **Build jar** dan kirim ke saya.
