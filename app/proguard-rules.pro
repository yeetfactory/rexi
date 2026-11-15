# Rules for the release build, on top of the ones libraries bring with them.

# Glance runs every home screen widget through WorkManager. WorkManager 2.7 keeps its input mergers but not their
# constructors, and creates one by name for every job, so R8's full mode left the widgets loading forever.
-keepclassmembers class * extends androidx.work.InputMerger {
    public <init>();
}

# Glance finds a widget's instances by its class when it updates them all. R8 merged the two widget classes into
# one, so updating every Today widget also redrew the Next dose widget with Today's layout. Keep them apart.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget

# Glance creates a widget button's action by name. Its own rule keeps the class without its constructor, so in
# R8's full mode the widget's Take button did nothing.
-keepclassmembers class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
