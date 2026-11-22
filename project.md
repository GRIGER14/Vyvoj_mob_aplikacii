# ReceiptTracker (Moje Výdavky) 📱🧾

> Moderná Android aplikácia na evidenciu osobných výdavkov, fotografovanie pokladničných blokov s automatickým OCR rozpoznávaním a geolokalizáciou nákupov na mape.  
> Vytvorené ako projekt pre predmet **Vývoj mobilných aplikácií**.

---

## 💡 Hlavná myšlienka a podstata projektu

**ReceiptTracker** rieši každodenný problém neprehľadných osobných financií a strácania papierových pokladničných blokov, ktoré časom blednú alebo sa zahodia. Aplikácia umožňuje:
1. **Zaznamenať nákup** (sumu, kategóriu, názov obchodu, dátum a poznámku).
2. **Odfotiť papierový bloček** priamo cez fotoaparát telefónu a priradiť ho k záznamu.
3. **Automaticky vyčítať sumu a obchod z bločku** pomocou lokálneho strojového učenia (**Google ML Kit OCR**) bez nutnosti manuálneho prepisovania.
4. **Zaznamenať presné GPS súradnice** nákupu a zobraziť všetky výdavky na interaktívnej mape.
5. **Analyzovať financie** cez animované kruhové grafy a exportovať kompletné výkazy do **PDF** a **CSV**.

Aplikácia funguje na princípe **Offline-First** — všetky dáta a fotografie ostávajú bezpečne uložené výhradne v pamäti telefónu.

---

## ✨ Kľúčové funkcionality (Features)

* 🧾 **Kompletné CRUD operácie:**
  * Pridávanie nových výdavkov s okamžitým súhrnom.
  * Chronologický prehľad s miniatúrami bločkov a filtrami podľa kategórií.
  * Úprava a bezpečné mazanie záznamov (vrátane fyzického odstránenia priradených fotografií z úložiska).
* 📷 **Integrácia fotoaparátu (Camera & FileProvider):**
  * Snímanie bločkov systémovou kamerou zariadenia (optimalizované pre fotoaparát Motorola Edge 40).
  * Ukladanie optimalizovaných fotografií do izolovaného interného úložiska aplikácie.
* 🤖 **Inteligentné OCR skenovanie (Google ML Kit):**
  * Plne offline analýza textu z bločku priamo v telefóne.
  * Automatické hľadanie kľúčových slov (*SPOLU, CELKOM, K ÚHRADE, SUMA*) a detekcia názvov obchodov (*Lidl, Billa, Tesco, Shell...*).
  * Možnosť doplniť údaje do formulára jedným kliknutím.
* 📍 **Geolokácia a mapa nákupov (OSMDroid):**
  * Automatický zber presných GPS súradníc cez `FusedLocationProviderClient`.
  * Interaktívna mapa s filtrami a špendlíkmi jednotlivých nákupov (bez nutnosti platených Google Maps kľúčov).
* 📊 **Vizuálna analytika a grafy:**
  * Animovaný **Donut Chart** (kruhový graf) vytvorený cez Jetpack Compose Canvas.
  * Percentuálne rozdelenie výdavkov podľa kategórií a prepínanie období (*Všetko / Tento mesiac*).
* 📤 **Export do PDF a CSV (Android ShareSheet):**
  * **PDF výkaz:** Generovanie viacstranového dokumentu s tabuľkou výdavkov a galériou reálnych fotografií bločkov.
  * **CSV export:** Štruktúrovaná tabuľka s kódovaním UTF-8 BOM pre bezchybné otvorenie v MS Excel a Google Sheets.

---

## 🛠️ Použitý technologický stack

| Oblasť | Použité technológie |
| :--- | :--- |
| **Programovací jazyk** | [Kotlin](https://kotlinlang.org/) (verzia 2.0.21) |
| **Používateľské rozhranie** | [Jetpack Compose](https://developer.android.com/jetpack/compose) + Material Design 3 |
| **Architektúra** | MVVM (Model-View-ViewModel) + Repository Pattern + StateFlow & Coroutines |
| **Lokálna databáza** | [Room Database](https://developer.android.com/training/data-storage/room) (SQLite abstrakcia) |
| **Strojové učenie (OCR)** | [Google ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/android) |
| **Lokalizácia (GPS)** | Google Play Services Location (`FusedLocationProviderClient`) |
| **Mapový podklad** | [OSMDroid](https://github.com/osmdroid/osmdroid) (OpenStreetMap for Android) |
| **Načítanie obrázkov** | [Coil Compose](https://coil-kt.github.io/coil/compose/) |
| **Export a dokumenty** | Natívny Android `PdfDocument` & `FileProvider` |

---

## 📂 Architektúra a štruktúra projektu

```text
sk.receipttracker/
├── MainActivity.kt                  # Hlavný vstupný bod a Compose NavHost
├── data/
│   ├── model/Expense.kt             # Room entita (ID, suma, kategória, GPS, cesta k foto)
│   ├── local/ExpenseDao.kt          # CRUD dopyty do databázy (Flow, Coroutines)
│   ├── local/AppDatabase.kt         # Singleton inštancia Room SQLite databázy
│   └── repository/ExpenseRepository.kt # Abstrakcia práce s dátami
├── ui/
│   ├── theme/                       # Farby, typografia a Material 3 téma
│   ├── viewmodel/ExpenseViewModel.kt# Správa stavu, výpočet súm, OCR a export
│   └── screens/
│       ├── ExpenseListScreen.kt     # Zoznam výdavkov, karta súčtu, filtre, export
│       ├── AddEditExpenseScreen.kt  # Formulár s kamerou, OCR a snímaním GPS
│       ├── ExpenseDetailScreen.kt   # Detail nákupu s plným náhľadom bločku a GPS
│       ├── AnalyticsScreen.kt       # Animovaný Donut graf a štatistika kategórií
│       └── MapScreen.kt             # OpenStreetMap so špendlíkmi nákupov
└── util/
    ├── FileHelper.kt                # Bezpečné generovanie URI a mazanie fotiek
    ├── LocationHelper.kt            # Získanie presnej polohy z GPS
    ├── OcrHelper.kt                 # ML Kit extrakcia cien a predajní z bločkov
    └── ExportHelper.kt              # Generovanie PDF reportov a CSV súborov
```

---

## 🚀 Požiadavky a spustenie projektu

### Požiadavky:
* **Android Studio** (Hedgehog, Iguana, Jellyfish, Koala alebo Ladybug).
* **JDK:** Verzia 17.
* **Minimálna verzia Androidu (minSdk):** Android 8.0 (API úroveň 26).
* **Cieľová verzia Androidu (targetSdk):** Android 14 (API úroveň 34).
* **Testované fyzické zariadenie:** **Motorola Edge 40** (Android 14).
