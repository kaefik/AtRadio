# AtRadio

[English](README.en.md) | [Tatar](README.tt.md) | [Russian](README.md)

An application for listening to online radio stations.

## Application Slogan

"All the radio in the world — in your pocket."

![Program Logo](https://github.com/kaefik/AtRadio/blob/main/images/logo.png)

## Program Features

- Listening to online radio stations.
- Switching between radio stations in the list.
- Saving up to 3 favorite radio stations for quick access to your favorite stations.
- Loading radio stations from a CSV file into the app (details on the file format are below).
- Saving the list of radio stations from the app to a CSV file.

## Supported Interface Languages

- English
- Tatar (Татарча)
- Bashkir (Башҡортса)
- Russian (Русский)

If you find any inaccuracies in the current languages or want to add your own language, feel free to contact me. See my contact information in the Contacts section.

## Application Requirements

This application has been tested on Android versions starting from 7.0.

Ideally, I would like my app to work on Android versions starting from 4.0.

## License

GNU GENERAL PUBLIC LICENSE Version 3.

Please always credit my authorship.

## Interface and Application Operation

When you first launch the app on your device, a dialog window will appear to select the interface language for the AtRadio app.

Also, at the first launch, several default radio stations are automatically available. To delete/add a station, see the "Working with Radio Station Lists" section.

The main part of the application’s home screen is the playback control panel:
- Play button
- Increase/decrease volume
- Previous/next radio station in the list.

Above the playback control buttons, on the left is a gear icon that opens the app's settings window, and on the right is the radio station list button.

Below the main control elements are three buttons for saving/playing Favorite (favorite) radio stations for quick access.

To save the current radio station as a favorite, long-press one of these buttons, and a dialog window will appear to confirm saving the current station as a favorite.

To play a favorite radio station, simply press one of the three favorite station buttons.

![Main Application Screen](https://github.com/kaefik/AtRadio/blob/main/images/screenAtRadio.jpg)

## Working with Radio Station Lists

Through the radio station list button on the main app window, the following features are available:

- Select a station from the list for playback.
- Delete a station by pressing the cross icon next to the station.
- Add a single station using the plus button.
- Save the list of radio stations to a CSV file and share it.
- Load a list of radio stations from a CSV file.

### CSV File Format

This is a text file where the first line is always "Name;URL." Each subsequent line contains the station's name and its URL (the station's audio stream link) separated by a semicolon.

```
Name;URL
название станции 1;url станции 1
```


## Application Settings

Through the gear button on the main screen, the following settings are available:
- Enable/disable screensaver (protection for smartphone screens, especially AMOLED displays, which often burn out due to static images).
- Enable/disable auto-play of the current radio station when the app starts.
- Change the app's language.
- Reset all settings to default. WARNING! This will also reset the radio station list to the default list. Therefore, it is recommended to save your current stations before resetting all settings. For how to do this, see the "Working with Radio Station Lists" section.

# Contacts

- Email: ilnursoft@gmail.com
- GitHub: [https://github.com/kaefik](https://github.com/kaefik)


