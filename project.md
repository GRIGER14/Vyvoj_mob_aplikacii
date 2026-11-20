# ReceiptTracker (Moje Výdavky)
> Projekt pre predmet: **Vývoj mobilných aplikácií**

---

### 1. Aký problém riešim (jednou vetou)
Aplikácia rieši problém neprehľadnej evidencie osobných výdavkov a strácania papierových pokladničných blokov tým, že umožňuje okamžite zaznamenať nákup s fotografiou bločku a presnou geolokáciou na mape.

---

### 2. Kto sú moji useri (Cieľová skupina)
- **Študenti a mladí profesionáli**, ktorí chcú mať pod kontrolou svoj mesačný rozpočet a vedieť, kde presne a na čo míňajú financie.
- **Zodpovední nakupujúci a rodiny**, ktorí potrebujú archivovať pokladničné bloky pre prípad reklamácie alebo záruky bez hromadenia papierov v peňaženke.
- **Živnostníci / Freelanceri / Cestovatelia**, ktorí potrebujú evidovať drobné bločky za obed, kávu, benzín či nákup materiálu spolu s miestom nákupu pre spätné vyúčtovanie.

---

### 3. Ako chcem problém vyriešiť (MVP – Minimum Viable Product)
Aplikácia v rámci MVP poskytne ucelený, spoľahlivý a jednoducho ovládateľný systém na správu osobných financií priamo v mobile:

1. **Kompletné CRUD operácie pre výdavky:**
   - **Create (Pridanie):** Zadanie sumy, názvu/popisu nákupu, dátumu a výberu kategórie (Jedlo, Doprava, Bývanie, Zábava, Zdravie, Iné).
   - **Read (Prehľad):** Chronologický zoznam všetkých výdavkov s prehľadným zobrazením kategórie, sumy, miniatúry bločku a celkovej sumy za obdobie.
   - **Update (Úprava):** Možnosť kedykoľvek upraviť chybnú sumu, zmeniť kategóriu alebo poznámku.
   - **Delete (Vymazanie):** Bezpečné odstránenie záznamu vrátane fyzického vymazania priradenej fotografie z úložiska.
2. **Integrácia fotoaparátu (Camera):**
   - Využitie knižnice **CameraX** na odfotenie bločku priamo v aplikácii alebo výber existujúcej fotky z galérie.
   - Trvalé uloženie optimalizovaného obrázka do interného úložiska aplikácie (App-specific storage).
   - Možnosť zobrazenia detailu bločku na celej obrazovke (Zoom/Detail view).
3. **Záznam a zobrazenie geolokácie (Location & Maps):**
   - Automatické získanie GPS súradníc (zemepisná šírka a dĺžka) v okamihu vytvárania záznamu cez **Fused Location Provider**.
   - Zobrazenie interaktívnej mapy (Google Maps SDK alebo OSMDroid), kde sú výdavky zobrazené ako špendlíky (markery) s detailom o cene a obchode po kliknutí.
4. **Lokálna perzistencia dát:**
   - Využitie **Room Database (SQLite)** pre rýchle, bezpečné a spoľahlivé ukladanie dát bez nutnosti internetového pripojenia (offline-first).

---

### 4. Čo v MVP nebude (Non-goals) – a prečo

| Funkcionalita (Non-goal) | Prečo to v MVP nebude |
| :--- | :--- |
| **OCR (Automatické rozpoznávanie textu a sumy z bločku)** | Implementácia ML Kit / Tesseract OCR vyžaduje pokročilé trénovanie modelov a riešenie rôznych formátov slovenských eKasa bločkov. V MVP používateľ zadá sumu manuálne za 2 sekundy, čo zaručuje 100% presnosť údajov. |
| **Synchronizácia s bankovými účtami (Open Banking API / PSD2)** | Vyžaduje bankové certifikácie, zložité bezpečnostné auditovanie, platené brány a prísne regulačné normy, čo presahuje rámec semestrálneho projektu. |
| **Cloudový backend a používateľská autentifikácia (Firebase/Auth)** | Primárnym cieľom je vytvoriť robustnú lokálnu aplikáciu s dôrazom na súkromie používateľa (privacy-by-design) a prácu s hardvérom telefónu (Camera, GPS, SQLite). Cloud pridáva závislosť na sieti a serveroch. |
| **Pokročilé zdieľanie rozpočtov medzi viacerými používateľmi** | Vyžadovalo by sieťovú infraštruktúru, riešenie konfliktov dát a správu používateľských rolí, čo odvádza pozornosť od kľúčovej funkcionality správy bločkov. |
| **Export do PDF / Excel formátov** | Nie je kritický pre základnú funkčnosť aplikácie. Dôraz sa kladie na vizualizáciu priamo v aplikácii a na mape. Pripravené ako rozšírenie po odovzdaní MVP. |

---

### 5. Aké technológie potrebujem

#### Jazyk a Architektúra:
- **Kotlin** – moderný, typovo bezpečný a oficiálne preferovaný jazyk pre Android vývoj.
- **Modern Android Architecture (MVVM + Clean Architecture principles)**:
  - **UI Layer:** Jetpack Compose (deklaratívne UI podľa Material Design 3) alebo XML Views.
  - **ViewModel & StateFlow / LiveData:** Oddelenie biznis logiky od životného cyklu obrazoviek.
  - **Repository Pattern:** Abstrakcia práce s dátami medzi databázou a UI.
  - **Kotlin Coroutines & Flow:** Asynchrónne spracovanie úloh na pozadí (čítanie z DB, snímanie polohy, ukladanie súborov).

#### Kľúčové knižnice a API:
- **Android Jetpack:**
  - **Room Database** (SQLite abstrakcia) – perzistentné ukladanie dát o výdavkoch (tabuľka transakcií, kategórií).
  - **Navigation Compose** – prechody medzi obrazovkami (Zoznam výdavkov, Pridanie/Editácia, Mapa, Detail bločku).
  - **Lifecycle & ViewModel** – správa stavu obrazoviek.
- **Práca s hardvérom telefónu:**
  - **CameraX** (`androidx.camera`) – moderná abstrakcia nad fotoaparátom Androidu (spoľahlivá práca s kamerou na zariadeniach ako Motorola Edge 40).
  - **Google Play Services Location** (`FusedLocationProviderClient`) – energeticky úsporné a presné získavanie GPS polohy.
- **Mapové podklady:**
  - **Google Maps SDK for Android** (alebo **OSMDroid** ako open-source alternatíva bez nutnosti API kľúča) – vizualizácia výdavkov na interaktívnej mape s markermi.
- **Spracovanie a načítanie obrázkov:**
  - **Coil** (alebo Glide) – rýchle asynchrónne načítavanie a cachovanie fotografií bločkov s podporou Jetpack Compose.
