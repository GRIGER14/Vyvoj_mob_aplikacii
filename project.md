# ReceiptTracker (Moje Výdavky)
> Semestrálny projekt pre predmet: **Vývoj mobilných aplikácií**

---

### 1. Aký problém riešim (jednou vetou)
Aplikácia rieši problém neprehľadnej evidencie osobných financií a strácania papierových pokladničných blokov tým, že umožňuje okamžite zaznamenať nákup s fotografiou bločku, automatickým vyčítaním údajov cez OCR a geolokáciou na mape.

---

### 2. Kto sú moji useri (Cieľová skupina)
* **Študenti a mladí ľudia**, ktorí chcú mať prehľad o svojom rozpočte a vidieť v grafoch, na čo najviac míňajú.
* **Bežní spotrebitelia a rodiny**, ktorí potrebujú bezpečne uchovávať bločky kvôli záruke (2-ročná lehota v SR/EÚ) alebo prípadnej reklamácii tovaru.
* **Živnostníci, cestovatelia a pracujúci**, ktorí potrebujú evidovať drobné výdavky (obed, benzín, materiál) spolu s polohou a jednoducho ich vyexportovať pre účtovníctvo.

---

### 3. Ako je problém vyriešený (Kľúčová funkcionalita)

Aplikácia funguje na princípe **offline-first** (100% lokálna perzistencia a ochrana súkromia):

1. **Kompletné CRUD operácie:**
   - Pridanie, zobrazenie chronologického zoznamu, úprava a bezpečné zmazanie výdavku (vrátane vymazania fyzického súboru fotografie z disku).
   - Filtrovanie podľa kategórií (*Jedlo, Doprava, Bývanie, Zábava, Zdravie, Nákupy, Iné*).
2. **Fotoaparát a správa fotografií bločkov:**
   - Odfotenie papierového pokladničného bloku cez systémovú kameru s využitím `FileProvider`.
   - Trvalé uloženie do interného úložiska aplikácie a zobrazenie v detaile nákupu.
3. **Automatické inteligentné OCR skenovanie (Google ML Kit):**
   - Priamo v zariadení (on-device) analyzuje odfotený bloček bez odosielania dát na internet.
   - Automaticky deteguje názov obchodnej siete (*Lidl, Billa, Tesco, Shell...*) a výslednú sumu (*SPOLU / CELKOM*), ktoré používateľ potvrdí jedným klikom.
4. **GPS geolokácia a interaktívna mapa:**
   - Automatické získanie súradníc v momente nákupu cez `FusedLocationProviderClient`.
   - Zobrazenie interaktívnej mapy (**OpenStreetMap / OSMDroid**) s filtrami a markermi nákupov.
5. **Vizuálna analytika a grafy:**
   - Animovaný kruhový graf (**Donut Chart** cez Jetpack Compose Canvas) s rozdelením výdavkov na kategórie.
   - Prepínanie časových období (*Všetko* / *Tento mesiac*).
6. **Export dát (Zdieľanie):**
   - Generovanie štruktúrovaného **CSV** súboru (s podporou Excelu a slovenskej diakritiky).
   - Generovanie vizuálneho viacstranového **PDF výkazu** s tabuľkou a miniatúrami odfotených bločkov.

---

### 4. Čo v projekte zámerne nie je (Non-goals) – a prečo

| Funkcionalita (Non-goal) | Prečo to v projekte nie je |
| :--- | :--- |
| **Priame napojenie na banky (PSD2 / Open Banking)** | Vyžaduje bankové licencie, zložité bezpečnostné audity a platené brány, čo presahuje rámec semestrálneho projektu. |
| **Cloudový backend a používateľské kontá (Firebase/Auth)** | Aplikácia stavia na princípe **Privacy-by-Design** – dáta a fotografie patria len používateľovi a ostávajú výhradne v pamäti jeho telefónu. |
| **Zdieľané rodinné rozpočty online** | Vyžadovalo by sieťovú infraštruktúru a synchronizáciu medzi zariadeniami, čo by odvádzalo pozornosť od kľúčového cieľa: práce s hardvérom (kamera, GPS, lokálna DB). |

---

### 5. Použité technológie a architektúra

* **Jazyk:** Kotlin (moderný štandard pre Android).
* **UI Framework:** Jetpack Compose + Material Design 3.
* **Architektúra:** MVVM (Model-View-ViewModel) + Repository Pattern + Kotlin Coroutines & StateFlow.
* **Databáza:** Room Database (SQLite abstrakcia).
* **Hardvér a senzory:**
  * Fotoaparát cez `ActivityResultContracts.TakePicture()` + `FileProvider`.
  * GPS senzor cez `FusedLocationProviderClient` (`play-services-location`).
* **Strojové učenie:** Google ML Kit Text Recognition (on-device OCR).
* **Mapy:** OSMDroid (OpenStreetMap bez nutnosti externých API kľúčov).
* **Spracovanie obrázkov:** Coil Compose.
* **Export:** Natívny `android.graphics.pdf.PdfDocument` a Android ShareSheet.
