# yav1
YaV1 is an Android app for the Valentine1 Radar Detector

YaV1 support: 
GitHub Issues: https://github.com/fanta8897/yav1/issues
RDF: https://www.rdforum.org/index.php?forums/159/

Historical Changes:
2.0.1
=====
- Auto lockout revamped, more reliable and faster. For users coming from 2.0.0 the lockout DB will be reset, check your lockout parameters, reset to default and restart the app.
- Overlay revamped, signal strength is now display
- Manual locking / white list is easier with a long click on the screen by poping up a dialog
- Other changes (Lockout Ka manual, Ka / Laser can be excluded under Savvy speed, vibrator can be set as well as front led for alerts.
- Some bug fixes

2.0.0
=====

- Auto lockout
- white list
- Tools tab
- Presets for settings - v1 - custom sweeps
1.1.5
=====

- Battery drain minimized
- V1 view revamped
- Gps location stabilized .
- Various performance enhancements

1.1.4
=====

- auto-stop option moved from advance to general display
- new option in General settings / GPS to show the direction in degrees from Noth 0 (could help in auto lockout testing)
- Youthan theme choice has been replaced by 3 theme Native (default) / USA (Rear yellow) / Euro (previous Youthan Theme), to make active, set the option, exit app and restart.
- New option in General Settings / Advanced => Broadcast raw alerts. Instead of ESP Library to call a CallBack function, the library would Broadcast the alerts, can solve Disconnection issues.
- Display setting name has been removed, it will always display now

1.1.3
=====
- Logged alert on Google Map
- Lockout safer and faster
- Dark mode option
- I/O toggle in data collection
- K-POP on/off in Euro mode
- Misc minor changes

1.0.9
=====

- Alert not logged when boxes disabled, fixed
- Laser alert for 1-2 second if YaV1 starts before V1, fixed
- Dynamic drift added, setting in Lockout Expert settings, for android >= 3.0 only
- Undo lockout,  last lockout alert or all bulk added can be undone, for android >= 3.0 only
