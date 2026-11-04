# Post-it

A personal, offline "text yourself" notes app for Android. A home-screen widget works like a
messenger: send yourself a note and the app files it by tag, adds it to a checklist, or sets a
reminder. Open the app to browse the organized feed, your lists, and upcoming reminders.

Nothing leaves the phone. There's no account and no server.

## What you can send

| Message | What happens |
| --- | --- |
| `app idea: plant tracker #ideas` | A note filed under `#ideas` |
| `remind me at 5 to call mom` | A reminder at 5 PM today, or tomorrow if 5 PM has passed |
| `remind me tomorrow at 9am to call the dentist` | Also works with today, tonight, weekdays, noon, "in 20 minutes" |
| `remind me every Monday at 8am to take out the trash` | Repeats. Also every day, every weekday, every Mon and Fri |
| `buy eggs, milk` | Two items on the Shopping list |
| `todo renew passport` | An item on the To-do list |
| `+movies Dune, Arrival` | Items on any list you name |
| `add tape to my hardware list` | The same, in plain words |

Long-press anything to delete it. You can also share a message from WhatsApp (or any app) to
**Save to Post-it** to file it. The share icon in the app exports everything as CSV.

## Building

Every push to `main` builds the APK with GitHub Actions and publishes it as the **latest**
release. To install it, download `post-it.apk` from the release on your phone and allow installs
from your browser when Android asks.

The app is signed with the fixed `app/debug.keystore`, so new builds install as updates and keep
your notes.

Building locally works too: open the folder in Android Studio and run it.
