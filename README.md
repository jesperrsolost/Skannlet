# Skannlet

Skannlet er en Android-app for lokal registrering av varer med strekkode. Skanninger organiseres i prosjekter og kan eksporteres som CSV og PDF/følgeseddel.

Appen er utviklet for Ø. M. Fjeld Prosjektservice og lagrer data lokalt på enheten. Den krever ingen brukerkonto, ekstern database eller skytjeneste.

Gjeldende versjon er **1.1.1**. Se [CHANGELOG.md](CHANGELOG.md) for endringshistorikk.

## Funksjoner

- Opprett utleverings- og returprosjekter med automatisk løpenummer.
- Skann med Zebra-skanner eller annen tastaturbasert strekkodeleser.
- Slå opp produktnavn fra en importert CSV-fil.
- Juster antall og rediger skannede varer før prosjektet låses.
- Se hvem som opprettet et prosjekt, og angre sletting av prosjekter og varer.
- Søk i prosjektlisten og bytt mellom lokale brukere.
- Eksporter prosjektdata som CSV og PDF/følgeseddel.
- Skriv ut strekkodeetiketter til TSC TC200 over lokalnett.
- Tilpass etikettmål og medietype for skriveren.

## Kom i gang

1. Start appen og opprett en lokal bruker.
2. Importer produktlisten fra `Profil` hvis du ønsker automatisk produktnavn.
3. Opprett eller velg et prosjekt under `Prosjekter`.
4. Åpne `Skanning` og skann varer.
5. Åpne prosjektdetaljene for å redigere, skrive ut eller eksportere.

### Produktliste

Produktlisten må være en CSV-fil med kolonnene `Produktnr.` og `Produkt`:

```csv
Produktnr.;Produkt
11063006;HS 63A/230V Skap
```

Semikolon, komma og tabulator støttes som skilletegn. Filen kan være kodet som UTF-8, UTF-8 med BOM eller Windows-1252. Når en ny fil importeres, erstatter den den tidligere produktlisten.

## Etikettskriver

Etikettutskrift er valgfritt og er laget for **TSC TC200**. Appen sender TSPL direkte over TCP, med port `9100` som standard.

Slik setter du opp skriveren:

1. Sørg for at Android-enheten og skriveren kan nå hverandre på samme lokalnett.
2. Åpne `Profil` og velg `Konfigurer` under `Etikettskriver`.
3. Skriv inn skriverens IPv4-adresse og port, og lagre.
4. Velg `Test tilkobling` for å kontrollere modell og status.
5. Velg etikettformat og skriv ut fra en varerad i prosjektdetaljene.

Det innebygde formatet `Small Barcode` er tilpasset etiketter på 35 × 14 mm med 2,25 mm mellomrom. Formatet kan kopieres og tilpasses. Etiketter med mellomrom, svartmerke og kontinuerlig materiale støttes.

## Eksport

Eksport oppretter en CSV-fil og forsøker å legge ved en PDF-følgeseddel i Androids delingsdialog. Følgeseddelen bruker A4-format, merkes som utlevering eller retur og inneholder prosjektinformasjon, oppretter, varer og tidspunkt.

Et prosjekt låses automatisk etter eksport for å hindre utilsiktede endringer. Det kan låses opp igjen fra prosjektdetaljene.

## Personvern og lagring

- Brukere, prosjekter, skanninger og produktliste lagres lokalt i appens private område.
- Skriveroppsett lagres lokalt og tas ikke med i Android-sikkerhetskopi eller enhetsoverføring.
- Appen har ingen innlogging, sporing eller ekstern synkronisering.
- Nettverk brukes bare ved direkte kommunikasjon med en konfigurert etikettskriver og når brukeren åpner eksterne lenker.

Avinstallering av appen sletter normalt de lokale appdataene.

## Installasjon

Installer den signerte APK-en fra prosjektets [GitHub Releases](https://github.com/jesperrsolost/Skannlet/releases). Android kan be om tillatelse til å installere apper fra den valgte nettleseren eller filbehandleren.

## Bygge fra kildekode

Du trenger Android Studio, Android SDK og JDK 11 eller Android Studios innebygde JBR.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Debug-APK bygges til:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Prosjektet bruker Kotlin, Jetpack Compose, Material 3, Navigation Compose og Kotlinx Serialization.

## Support

Supportvalget i appen forsøker å åpne TeamViewer QuickSupport. Feil og forbedringsforslag kan også registreres som en [GitHub Issue](https://github.com/jesperrsolost/Skannlet/issues).

## Lisens

Kildekoden er tilgjengelig under [Apache License 2.0](LICENSE).
