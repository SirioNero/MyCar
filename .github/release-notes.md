myCar 0.7.5-alpha: a clear word when a VPN keeps myCar off the car's Wi-Fi, and for car apps that refuse myCar. From your reports on the GrapheneOS forum.

## Wireless and VPNs
- If a VPN on the phone keeps myCar inside it, Android won't let myCar use the car's Wi-Fi (the log said "EPERM" over and over, and the start-up never got through). myCar now says so within seconds, on Home and in its notification (open it to read it all), with what to do: leave myCar out of the VPN (its split tunneling), pause the VPN for the drive, or use the cable. With "Block connections without VPN" on, Android blocks the apps left out too. Tailscale is called by its name.
- As soon as no VPN carries myCar any more, the tries go back to the car's Wi-Fi at once, without Disconnect and Connect.
- How to connect has a step for VPNs, shown only while a VPN carries myCar, with a button to Android's VPN settings.

## Car apps that refuse myCar
- Some car apps (ABRP, for one) let in only the hosts their developer chose, and turn myCar away at the start ("Unknown host"). myCar can't get around that. Now the car says it in plain words instead of the library's error, the app gets a red X and no longer starts by itself (as the map at the start of a drive, beside the map, in the pages), and a tap on its tile still tries it. This is forgotten when the app is updated or accepts myCar. A Waze modified with Morphe keeps its own message, with the line to add to the patch.
- The map page no longer offers "Reopen" for a map that won't start: it asks you to choose a navigation app.

## Cars
- Volkswagen Golf 8 (2023, with the CarService certificate) and Hyundai Inster (2026, wired) join the list of cars that work. Thanks for the reports.

## Checked
- Built and unit-tested in CI, with new tests for when to speak of a VPN and for which refusals last. Not tried with a real VPN in the way, nor with ABRP: the emulator has neither.
- Nothing changes when no VPN is in the way, nor for the apps that accept myCar.
