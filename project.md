# ReceiptTracker — Technická a funkčná špecifikácia systému
**Predmet:** Vývoj mobilných aplikácií (Zimný semester)  
**Cieľová platforma:** Android (minSdk: 26 / Android 8.0 Oreo, targetSdk: 34 / Android 14)  
**Programovací jazyk:** Kotlin 2.0.21  
**UI Framework:** Jetpack Compose (Material Design 3)  
**Architektonický vzor:** MVVM (Model-View-ViewModel) + Unidirectional Data Flow (UDF) + Repository Pattern  

---

## 1. Účel dokumentu a systémový kontext

Tento dokument slúži ako záväzná technická a funkčná špecifikácia pre vývoj, testovanie a overovanie mobilnej aplikácie **ReceiptTracker**. Definuje systémovú architektúru, dátové štruktúry, rozhrania, hardvérové integrácie, správanie jednotlivých komponentov a pravidlá spracovania dát.

Dokument je koncipovaný tak, aby poskytoval exaktné inštrukcie pre softvérového inžiniera alebo autonómneho AI agenta bez použitia vágnych formulácií či propagačného jazyka.

### 1.1 Systémové hranice (System Boundaries)
* **Systém beží v režime Offline-First:** Všetka perzistencia, analytické agregácie a strojové spracovanie obrazu prebiehajú výhradne lokálne na klientskom zariadení.
* **Žiadna cloudová závislosť:** Aplikácia nekomunikuje so žiadnym vzdialeným REST API ani externou databázou.
* **Integrácie so systémom Android:**
  * Fotoaparát a správa obrazových dát cez `androidx.activity.result.contract.ActivityResultContracts` a `androidx.core.content.FileProvider`.
  * Geonapájanie a GPS senzory cez `com.google.android.gms.location.FusedLocationProviderClient`.
  * Strojové učenie na zariadení cez `com.google.android.gms.vision / ML Kit Text Recognition`.
  * Zobrazovanie mapových podkladov cez knižnicu `OSMDroid (OpenStreetMap)`.
  * Zdieľanie výstupných dát cez systémový `Intent.ACTION_SEND`.

---

## 2. Architektúra systému a návrhové vzory

Aplikácia dôsledne implementuje štandardy modernej Android architektúry (Modern Android Development — MAD):

```
┌────────────────────────────────────────────────────────┐
│               UI LAYER (Jetpack Compose)               │
│  ExpenseListScreen  |  AddEditExpenseScreen  |  ...    │
└───────────────────────────▲────────────────────────────┘
                            │ StateFlow / Events
┌───────────────────────────┴────────────────────────────┐
│              VIEWMODEL (ExpenseViewModel)               │
│   UDF State Holders, Business Logic, Flow Combine      │
└───────────────────────────▲────────────────────────────┘
                            │ Coroutines (suspend) / Flow
┌───────────────────────────┴────────────────────────────┐
│                    REPOSITORY LAYER                    │
│                   ExpenseRepository                    │
└───────────────▲────────────────────────▲───────────────┘
                │                        │
┌───────────────┴───────────────┐ ┌──────┴───────────────┐
│     LOCAL DATABASE (Room)     │ │   HARDWARE / UTILS   │
│  AppDatabase / ExpenseDao     │ │ OcrHelper / Location │
└───────────────────────────────┘ └──────────────────────┘
```

### 2.1 Rozdelenie vrstiev
1. **Presentation Layer (`sk.receipttracker.ui`):**
   * Deklaratívne používateľské rozhranie napísané v Jetpack Compose.
   * Obrazovky sú bezstavové (stateless), stav je delegovaný smerom nahor do `ExpenseViewModel`.
   * Komunikácia prebieha cez `StateFlow` (dáta smerom k UI) a lambda funkcie / eventy (akcie smerom do ViewModelu).
2. **Business / ViewModel Layer (`sk.receipttracker.ui.viewmodel`):**
   * `ExpenseViewModel` riadi aplikačnú logiku, reaktívne prepája toky dát (`combine`), spravuje filtre a volá asynchrónne operácie v `viewModelScope`.
3. **Data Layer (`sk.receipttracker.data`):**
   * `ExpenseRepository`: Abstrakčná vrstva oddeľujúca databázovú implementáciu od ViewModelu.
   * `AppDatabase` & `ExpenseDao`: Room ORM abstrakcia nad SQLite databázou s reaktívnymi dopytmi (`Flow`).
4. **Hardware & Utility Layer (`sk.receipttracker.util`):**
   * Izolované jednoúčelové moduly pre OCR analýzu, GPS geolokáciu, správu súborov a export dát.

---

## 3. Dátový model a špecifikácia perzistencie

### 3.1 Entita: `Expense` (Tabuľka `expenses`)
Entita reprezentuje záznam o jednotlivom nákupe / výdavku.

| Názov stĺpca | Dátový typ (Kotlin) | SQLite typ | Obmedzenia | Popis |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `Long` | `INTEGER` | `PRIMARY KEY AUTOINCREMENT` | Jednoznačný identifikátor záznamu |
| `title` | `String` | `TEXT` | `NOT NULL` | Názov nákupu alebo obchodného subjektu |
| `amount` | `Double` | `REAL` | `NOT NULL` | Finančná čiastka v mene EUR (kladné číslo) |
| `category` | `String` | `TEXT` | `NOT NULL` | Názov kategórie výdavku |
| `date` | `Long` | `INTEGER` | `NOT NULL` | Unix timestamp v milisekundách |
| `photoPath` | `String?` | `TEXT` | `NULLABLE` | Absolútna cesta k uloženému JPG súboru v privátnom úložisku |
| `latitude` | `Double?` | `REAL` | `NULLABLE` | Zemepisná šírka (WGS 84) v stupňoch |
| `longitude` | `Double?` | `REAL` | `NULLABLE` | Zemepisná dĺžka (WGS 84) v stupňoch |
| `note` | `String` | `TEXT` | `NOT NULL DEFAULT ''` | Doplňujúca textová poznámka |

### 3.2 Doplnkové dátové modely
* **`StoreInsight`:** Agregovaný model pre analýzu obchodov na mape (`storeName: String`, `totalAmount: Double`, `count: Int`, `latitude: Double`, `longitude: Double`, `latestCategory: String`, `latestDate: Long`).
* **`CategoryStat`:** Model pre štatistický rozpad kategórií (`category: String`, `total: Double`, `percentage: Float`, `count: Int`).
* **`OcrResult`:** Výsledok rozpoznávania textu (`detectedStore: String?`, `detectedAmount: Double?`, `rawText: String`).

### 3.3 Rozhranie `ExpenseDao` — Špecifikácia SQL operácií
* `getAllExpenses(): Flow<List<Expense>>` — Zoradené zostupne podľa `date DESC`.
* `getExpenseById(id: Long): Flow<Expense?>` — Vyhľadanie záznamu podľa primárneho kľúča.
* `getExpensesWithLocation(): Flow<List<Expense>>` — Filtrovanie záznamov, kde `latitude IS NOT NULL AND longitude IS NOT NULL`.
* `searchExpenses(query: String?, category: String?, fromMillis: Long?, toMillis: Long?): Flow<List<Expense>>` — Komplexný SQL dopyt s dynamickou filtráciou textu (title, note), kategórie a časového rozsahu.
* `insertExpense(expense: Expense): Long` — Vloženie záznamu s `OnConflictStrategy.REPLACE`.
* `updateExpense(expense: Expense)` — Aktualizácia existujúceho záznamu.
* `deleteExpense(expense: Expense)` — Fyzické odstránenie záznamu.

---

## 4. Funkčné požiadavky (Functional Requirements)

### FR-01: Evidencia a validácia výdavkov
* **Vstupy:** Názov obchodu (`title`), Suma (`amount`), Kategória (`category`), Dátum (`date`), Poznámka (`note`), Fotografia (`photoPath`), GPS súradnice (`latitude`, `longitude`).
* **Validačné pravidlá:**
  1. Pole `title` nesmie byť po orezaní bielych znakov (`trim()`) prázdne.
  2. Pole `amount` musí byť kladné desatinné číslo (`amount > 0.0`), podporujúce maximálne 2 desatinné miesta. Čiarka `,` je na vstupe automaticky normalizovaná na bodku `.`.
* **Výstup:** Uloženie nového záznamu do Room databázy alebo aktualizácia existujúceho záznamu podľa `expenseId`.

### FR-02: Správa kategórií a prioritizácia
* **Predvolený zoznam kategórií:** `["Jedlo", "Doprava", "Bývanie", "Zábava", "Zdravie", "Nákupy", "Iné"]`.
* **Dynamické pridávanie:** Používateľ má možnosť vo formulári pridať vlastnú kategóriu (`+ Pridať novú kategóriu...`).
* **Pravidlo radenia:** Všetky existujúce a novo pridané kategórie sa dynamicky načítavajú z databázy. Kategória `"Iné"` musí zostať **vždy na poslednom mieste** vo všetkých výberových zoznamoch a filtrovacích lištách.

### FR-03: Integrácia fotoaparátu a správa súborov
* **Mechanizmus snímania:** Použitie Android `ActivityResultContracts.TakePicture()`.
* **Bezpečnosť súborov:** Využitie `FileProvider` so špecifikáciou v `res/xml/file_paths.xml` mapujúcou privátny adresár `external-files-path`. URI nesmie odhaľovať skutočnú cestu na disku (`file://` je zakázané, povolené je len `content://sk.receipttracker.fileprovider/...`).
* **Čistenie disku:** Pri odstránení záznamu `Expense` je automaticky vymazaný aj priradený súbor fotografie na disku cez `FileHelper.deleteImageFile()`.

### FR-04: On-Device OCR extrakcia údajov (Google ML Kit)
* **Vstup:** Súbor obrázka zachyteného fotoaparátom alebo vybraného z galérie.
* **Technológia:** `com.google.android.gms.vision / ML Kit Text Recognition` (beží lokálne bez prístupu k sieti).
* **Parsovací algoritmus:**
  1. Hľadanie kľúčových slov predajcov v prvých riadkoch rozpoznaného textu (Lidl, Tesco, Billa, Kaufland, dm, Shell, OMV, McDonald's a ďalšie).
  2. Regex extrakcia peňažných súm hľadaním vzorov `\d+[,.]\d{2}\s*(?:€|EUR)?` pri kľúčových slovách `SPOLU`, `SUMA`, `TOTAL`, `K ÚHRADE`.
  3. Automatické predvyplnenie polí formulára s možnosťou manuálnej korekcie používateľom.

### FR-05: Získavanie geolokačných súradníc (GPS)
* **Technológia:** Google Play Services Location API (`FusedLocationProviderClient`).
* **Oprávnenia:** `ACCESS_FINE_LOCATION` a `ACCESS_COARSE_LOCATION`.
* **Algoritmus:**
  1. Pokus o získanie aktuálnej polohy s vysokou presnosťou (`Priority.PRIORITY_HIGH_ACCURACY`) cez `CancellationTokenSource`.
  2. Fallback na `fusedClient.lastLocation` v prípade timeoutu alebo nedostupnosti priameho GPS fixu.
  3. Ošetrenie chýbajúceho hardvéru alebo vypnutých lokalizačných služieb bez pádu aplikácie.

### FR-06: Reaktívny zoznam a filtrovanie nákupov
* **Predvolený stav (Default View):** Zoznam zobrazuje nákupy **výhradne za aktuálny kalendárny mesiac** (`isCurrentMonthOnly = true`). Horná súhrnná karta zobrazuje `Výdavky za tento mesiac (názov mesiaca rok)`.
* **Zobrazenie všetkých nákupov:** Tlačidlo `Zobraziť všetky nákupy` prepne zoznam do globálneho zobrazenia. Zároveň sa sprístupní tlačidlo `Späť na tento mesiac`.
* **Full-textové vyhľadávanie:** Integrovaná vyhľadávacia lišta v `TopAppBar` filtruje záznamy v reálnom čase podľa reťazca v názve (`title`) alebo v poznámke (`note`).
* **Kategóriový filter:** Horizontálna lišta čipov umožňujúca filtrovať zoznam podľa zvolenej kategórie.

### FR-07: Kalendárový dialóg (`ExpenseCalendarDialog`)
* **Vizuálna reprezentácia:** Mesačná matica 7x6 so slovenskými skratkami dní (`Po, Ut, St, Št, Pi, So, Ne`).
* **Indikátory nákupov:**
  * Dni, v ktorých bol zaevidovaný aspoň jeden nákup, sú označené vizuálnym bodovým indikátorom.
  * Dni s viacerými nákupmi zobrazujú ikonu pokladničného bloku (`Receipt`).
* **Filtrovanie:** Výberom dňa v kalendári na hlavnej obrazovke sa zoznam okamžite vyfiltruje na daný konkrétny deň.

### FR-08: Filtrovanie podľa intervalu dátumov (`DateRangePicker`)
* **Komponent:** Material 3 `DateRangePicker` v modálnom dialógu.
* **Rozsah:** Používateľ zvolí počiatočný (`Od`) a koncový (`Do`) dátum. Filter ohraničí záznamy od `00:00:00.000` prvého dňa do `23:59:59.999` posledného dňa.
* **Zrušenie filtra:** Zobrazený aktívny čip s krížikom `✕` umožňuje okamžitý návrat do predvoleného stavu.

### FR-09: Mapová vizualizácia a Store Insights
* **Mapový engine:** OSMDroid MapView s podkladmi TileSourceFactory.MAPNIK.
* **Režimy zobrazenia:**
  1. *Store Insights (Klastrovanie):* Výdavky z rovnakej lokality sú agregované do jedného klastrového markera zobrazujúceho počet nákupov (napr. `14x`) a celkovú minutú sumu (napr. `320 €`).
  2. *Jednotlivé bločky:* Zobrazenie samostatných pinov pre každý záznam.
* **Interaktivita:** Kliknutím na marker sa v dolnej časti obrazovky animovane vysunie informačná karta (`StoreInsightCard`) s celkovou bilanciou, počtom návštev a dátumom posledného nákupu v danej predajni.

### FR-10: Analytika a vizualizácia rozdelenia výdavkov
* **Kruhový graf (Donut Chart):** Vykresľovaný cez Compose `Canvas` pomocou metódy `drawArc` s plynulou animáciou (`Animatable`, `tween(800)`).
* **Časové dimenzie:**
  * `Všetko` (celá história)
  * `Tento mesiac` (od 1. dňa aktuálneho mesiaca)
  * `Vybrať mesiac` (výber ľubovoľného mesiaca a roka cez `MonthPickerDialog` s krokovaním cez šípky `‹` a `›`).
* **Metriky:** Celkový objem výdavkov, presný počet nákupov a percentuálny podiel jednotlivých kategórií.

### FR-11: Export do formátu PDF
* **Technológia:** Natívne Android API `android.graphics.pdf.PdfDocument`.
* **Štruktúra dokumentu:**
  * Strana formátu A4, hlavička s dátumom generovania a celkovou bilanciou.
  * Formátovaná tabuľka s položkami: Dátum, Názov, Kategória, Suma.
  * Fotogaléria bločkov (downscalované bitmapy vykreslené v 2-stĺpcovej mriežke).
* **Distribúcia:** Súbor je uložený do cache a odoslaný cez `Intent(Intent.ACTION_SEND)` s MIME typom `application/pdf`.

### FR-12: Export do formátu CSV
* **Špecifikácia formátu:** Štandard RFC 4180.
* **Kódovanie:** UTF-8 s hlavičkou: `ID;Názov;Suma;Mena;Kategória;Dátum;Poznámka;GPS_Lat;GPS_Lon;Má_Fotku`.
* **Distribúcia:** Zdieľanie cez Android Sharesheet s MIME typom `text/csv`.

---

## 5. Nefunkčné požiadavky (Non-Functional Requirements)

* **NFR-01 (Kompatibilita):** Min SDK 26 (Android 8.0), Target SDK 34 (Android 14). Plná podpora moderných verzií JVM (Java 17/21).
* **NFR-02 (Offline autonómia):** Aplikácia musí fungovať na 100 % bez sieťového pripojenia. Zlyhanie sieťového soketu nesmie spôsobiť žiadnu chybu ani pád.
* **NFR-03 (Výkon a pamäť):**
  * Snímky bločkov nesmú byť držané v plnom rozlíšení v operačnej pamäti. Načítanie cez knižnicu `Coil` musí využívať automatické vzorkovanie (subsampling).
  * Vykresľovanie zoznamu výdavkov musí byť plynulé (60 fps) vďaka `LazyColumn` s explicitným kľúčom položky `key = { it.id }`.
* **NFR-04 (Integrita dát):** Zmeny v databáze sa vykonávajú v transakciách. Zmazanie záznamu z databázy je spriahnuté s asynchrónnym odstránením prislúchajúceho fyzického súboru fotografie.
* **NFR-05 (Používateľská odozva):** Všetky blokujúce IO operácie (čítanie z disku, generovanie PDF/CSV, ML Kit inferencia) musia bežať na `Dispatchers.IO` a nesmú blokovať hlavné vlákno (Main Thread).
* **NFR-06 (Lokalizácia):** Používateľské rozhranie a formátovanie meny/dátumov rešpektuje slovenskú lokalizáciu (`Locale("sk", "SK")`).

---

## 6. Špecifikácia obrazoviek a navigačný graf

Navigácia je riadená komponentom `NavHost` so štartovacím bodom `Screen.ExpenseList.route`.

```
                  ┌──────────────────────┐
                  │  ExpenseListScreen   │
                  │ (Hlavný zoznam nák.) │
                  └──────────┬───────────┘
         ┌───────────────────┼───────────────────┐
         │ (FAB / Edit)      │ (Položka zoznamu) │ (TopAppBar)
         ▼                   ▼                   ▼
┌──────────────────┐ ┌──────────────────┐ ┌──────────────────┐
│AddEditExpenseScr.│ │ExpenseDetailScr. │ │   MapScreen      │
│(Formulár, OCR)   │ │(Náhľad bločku)   │ │(Klastrovaná mapa)│
└──────────────────┘ └─────────┬────────┘ └──────────────────┘
                               │ (Upraviť)               ▲
                               └─────────►───────────────┤ (Zobraziť na mape)
                                                         │
                                                ┌────────┴─────────┐
                                                │ AnalyticsScreen  │
                                                │ (Donut, Mesiace) │
                                                └──────────────────┘
```

1. **`ExpenseListScreen` (Trasa: `expense_list`):**
   * Prehľad nákupov za aktuálny mesiac / celkovo, full-text vyhľadávanie, filtrovacie čipy, dialóg kalendára, dialóg výberu obdobia, dialóg exportu.
2. **`AddEditExpenseScreen` (Trasa: `add_expense`, `edit_expense/{expenseId}`):**
   * Vstupné textové polia, výber kategórie s tvorbou novej položky, integrácia kamery / galérie, OCR procesor, tlačidlo GPS lokalizácie.
3. **`ExpenseDetailScreen` (Trasa: `expense_detail/{expenseId}`):**
   * Detailná vizualizácia konkrétneho nákupu, plná fotografia bločku, geolokačné údaje, akcie pre úpravu a bezpečné zmazanie.
4. **`MapScreen` (Trasa: `map`):**
   * Interaktívna mapa, prepínač klastrovania Store Insights, vysúvacia vizitka predajne.
5. **`AnalyticsScreen` (Trasa: `analytics`):**
   * Grafický rozbor výdavkov, prepínanie časových období, volič mesiacov, zoznam kategórií.

---

## 7. Pokyny pre implementáciu, verifikáciu a testovanie

Pri rozširovaní alebo úprave zdrojového kódu musí agent dodržiavať nasledujúce pravidlá:
1. **Zákaz mutácie stavu mimo ViewModelu:** Všetky stavové premenné ovplyvňujúce dáta musia prechádzať cez `ExpenseViewModel`. V Composable funkciách sa používa iba `collectAsState()`.
2. **Konzistencia kategórií:** Pri pridávaní vlastnej kategórie do zoznamu musí systém garantovať, že kategória `"Iné"` je vždy zaradená na samom konci výsledného zoznamu.
3. **Kompilácia a build:** Každá zmena musí byť overená spustením Gradle úlohy `:app:assembleDebug`. Kód nesmie obsahovať neoverené referencie ani potlačené chyby typu `Unresolved reference`.
4. **Testovací kontrolný zoznam (Acceptance Test Checklist):**
   - [ ] Vytvorenie nákupu s manuálnym vyplnením a uloženie do databázy.
   - [ ] Vytvorenie nákupu cez OCR snímanie bločku a správne predvyplnenie sumy.
   - [ ] Zaznamenanie GPS polohy a jej zobrazenie v detaile aj na mape.
   - [ ] Zoskupenie markerov rovnakej predajne do klastra a zobrazenie štatistiky po kliknutí.
   - [ ] Filtrovanie zoznamu podľa vyhľadávacieho reťazca, kategórie a dátumového rozsahu.
   - [ ] Prepnutie mesiaca v analytike a korektný prepočet počtu nákupov a sumy.
   - [ ] Vygenerovanie a úspešné otvorenie PDF a CSV exportu.
