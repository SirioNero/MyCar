myCar 0.7.6-alpha: turns on the instrument cluster for cars that want pictures, a guard for the phone's Bluetooth address, Cinema for NewPipe and shared links, and an app grid that scrolls up and down. From your reports on the GrapheneOS forum and on GitHub.

## Turns on the instrument cluster
- Some cars want each turn on the instrument cluster (the dials behind the steering wheel) as a picture, and no more often than they say. An MG ZS 2024 (issue #23) hung up half a second after the first turn of every route: myCar sent the turn without a picture, in the same instant as the status and the distance. Now myCar reads what the car asks for. Cars that want pictures get the map app's own turn icon (or an arrow drawn by myCar) at the size they ask, one message at a time, as often as they allow. Cars that take turn codes get them as before, at their pace.
- Safety net: if a car still hangs up twice in a row within 3 seconds of a route starting, myCar sends that car no more turns (the map on the car's screen keeps showing them) and says so on the next drive, while parked. The car's Settings > Connection then shows "Instrument cluster: No turns for this car", with "Send turns again".

## The phone's Bluetooth address
- Since 0.7.4 the phone's Bluetooth address can be written in Settings > Connection, for the cars that need it. Some cars then stop taking the phone's music over Bluetooth (and with the Automatic sound, Android asks to share the screen at every drive). myCar now notices it: if a car played the phone's music over Bluetooth on a drive without the address, and stops once it is told, myCar stops telling that car (only that one) and says so on Home, on the car while parked, and under the address in Settings. From the next drive that car can take the music over Bluetooth again.
- The setting now says which cars need the address, and that it can take Bluetooth music away from others. If you wrote it in 0.7.4 or 0.7.5, myCar learns a car only after a drive of at least 5 minutes without it: if your music over Bluetooth stopped since then, clear the address.

## Cinema for other apps
- myCar shows up as "myCar · Cinema" among NewPipe's external players and in Android's share menu. A video sent from NewPipe (or a similar app) plays in Cinema on the car's screen; a shared link opens on its site in Cinema. Cinema's rules stay the same: videos only while parked.
- A shared web page, or a video from an address on your home network, opens only after you say yes on the car's screen: any app can send one, and myCar can reach your home network. Videos from the internet open straight away.
- Only web addresses (http and https), no files. With no car connected, myCar says so and keeps nothing.
- In a stream without a length (a live one), Cinema's ±10 s buttons no longer take it back to the start.

## App grid and Settings
- New "App grid" choice: Pages (sideways, as before) or List (up and down), in the car's Settings and on the phone under Car screen. The list has the same icons and columns, and the knob moves through it.
- The car's Settings stay where you left them for the whole drive. They used to jump back to the top whenever another screen opened over them or the car's picture was rebuilt.

## Cars
- Hyundai Inster (2026, wired, with the CarService certificate), Hyundai Ioniq 5 (2023, wireless through an Ottocast Mini adapter) and Jeep Cherokee (2027) join the list of cars that work. The MG ZS hybrid (2024) is listed with its navigation problem until a driver confirms the fix. Thanks for the reports.

## Checked
- Built and unit-tested in CI, with new tests for the cluster's pace, pictures and safety net, the Bluetooth address guard, the links Cinema accepts, and the place the Settings keep. The car preview in CI shows the cluster pictures, the list grid, the Settings keeping their place, and Cinema opening a video and a shared page from another app.
- Not tried with a real car: not the MG's cluster, not a car that drops Bluetooth music when told the address, and not NewPipe itself (in CI the videos come from a test server).
- Nothing changes for cars without a cluster, for cars that take turn codes, without the address written, and with the grid on Pages.
